package com.example.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dashboard.pipeline.EtlPipeline;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Tag("perf")
class PerformanceTest extends AbstractIntegrationTest {

	private static final Logger log = LoggerFactory.getLogger(PerformanceTest.class);

	private static final int TOTAL = 1_000_000;

	private static final Duration NIGHT_WINDOW = Duration.ofHours(4);

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

	@Test
	void should_load_million_rows_within_night_window() {
		log.info("Seeding {} rows into source...", TOTAL);
		sourceWriter.update("""
				INSERT INTO products (sku, name, price, updated_at)
				SELECT 'SKU-' || g, 'Product ' || g, (random() * 1000)::numeric(12,2),
				       now() - interval '1 minute'
				FROM generate_series(1, ?) g
				""", TOTAL);

		long started = System.nanoTime();
		long runId = pipeline.run();
		Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
		log.info("Full run of {} rows finished in {} (runId={})", TOTAL, elapsed, runId);

		assertThat(mart.queryForObject("SELECT count(*) FROM products_mart", Long.class)).isEqualTo(TOTAL);
		assertThat(elapsed).isLessThan(NIGHT_WINDOW);
	}

}
