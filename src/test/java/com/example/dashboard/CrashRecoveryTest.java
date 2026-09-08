package com.example.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.example.dashboard.pipeline.EtlPipeline;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "etl.batch-size=500")
class CrashRecoveryTest extends AbstractIntegrationTest {

	private static final int TOTAL = 20_000;

	private final JdbcTemplate sourceWriter = new JdbcTemplate(
			new DriverManagerDataSource(SOURCE_DB.getJdbcUrl(), SOURCE_DB.getUsername(), SOURCE_DB.getPassword()));

	@Autowired
	@Qualifier("martJdbcTemplate")
	private JdbcTemplate mart;

	@Autowired
	private EtlPipeline pipeline;

	@BeforeEach
	void clean() {
		sourceWriter.update("DELETE FROM products");
		mart.update("TRUNCATE staging_raw, etl_error, products_mart, etl_checkpoint, etl_run CASCADE");
	}

	private void seed(int count) {
		sourceWriter.update("""
				INSERT INTO products (sku, name, price, updated_at)
				SELECT 'SKU-' || g, 'Product ' || g, 1.00, now() - interval '1 minute'
				FROM generate_series(1, ?) g
				""", count);
	}

	private long martCount() {
		return mart.queryForObject("SELECT count(*) FROM products_mart", Long.class);
	}

	private long stagedCount() {
		return mart.queryForObject("SELECT count(*) FROM staging_raw", Long.class);
	}

	private String lastRunStatus() {
		return mart.queryForObject("SELECT status FROM etl_run ORDER BY id DESC LIMIT 1", String.class);
	}

	@Test
	void should_resume_from_last_checkpoint_when_interrupted_between_batches() throws InterruptedException {
		seed(TOTAL);

		Thread worker = new Thread(() -> {
			try {
				pipeline.run();
			}
			catch (Exception ignored) {
				// прогон прерван — ожидаемое поведение сценария
			}
		});
		worker.start();
		await().atMost(Duration.ofSeconds(60)).until(() -> stagedCount() >= 1000);
		worker.interrupt();
		worker.join(Duration.ofSeconds(60).toMillis());

		assertThat(lastRunStatus()).isEqualTo("FAILED");
		assertThat(martCount()).isLessThan(TOTAL);

		long secondRunId = pipeline.run();

		assertThat(martCount()).isEqualTo(TOTAL);
		var run = mart.queryForMap("SELECT status, rows_loaded FROM etl_run WHERE id = ?", secondRunId);
		assertThat(run.get("status")).isEqualTo("SUCCESS");
		assertThat(((Number) run.get("rows_loaded")).longValue()).isEqualTo(TOTAL - mart.queryForObject(
				"SELECT count(*) FROM staging_raw WHERE run_id != ?", Long.class, secondRunId));
	}

	@Test
	void should_fail_run_without_loading_when_interrupted_before_first_batch() {
		seed(100);
		Thread.currentThread().interrupt();
		try {
			assertThatThrownBy(pipeline::run).hasMessageContaining("interrupted");
		}
		finally {
			Thread.interrupted(); // очистка флага, чтобы не ломать дальнейшие JDBC-вызовы
		}

		assertThat(lastRunStatus()).isEqualTo("FAILED");
		assertThat(martCount()).isZero();

		pipeline.run();
		assertThat(martCount()).isEqualTo(100);
	}

}
