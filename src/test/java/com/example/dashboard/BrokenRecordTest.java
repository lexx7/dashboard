package com.example.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dashboard.pipeline.EtlPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class BrokenRecordTest extends AbstractIntegrationTest {

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
	void should_quarantine_broken_records_when_price_is_negative() {
		sourceWriter.update("""
				INSERT INTO products (sku, name, price, updated_at)
				SELECT 'SKU-' || g, 'Product ' || g, CASE WHEN g <= 5 THEN -1.00 ELSE 1.00 END,
				       now() - interval '1 minute'
				FROM generate_series(1, 1000) g
				""");

		long runId = pipeline.run();

		assertThat(mart.queryForObject("SELECT count(*) FROM products_mart", Long.class)).isEqualTo(995);
		assertThat(mart.queryForObject("SELECT count(*) FROM etl_error WHERE run_id = ?", Long.class, runId))
			.isEqualTo(5);
		assertThat(mart.queryForObject("SELECT count(DISTINCT reason) FROM etl_error WHERE run_id = ?", Long.class,
				runId))
			.isGreaterThanOrEqualTo(1);

		var run = mart.queryForMap("SELECT status, rows_staged, rows_loaded, rows_failed FROM etl_run WHERE id = ?",
				runId);
		assertThat(run.get("status")).isEqualTo("SUCCESS");
		assertThat(((Number) run.get("rows_staged")).longValue()).isEqualTo(1000);
		assertThat(((Number) run.get("rows_loaded")).longValue()).isEqualTo(995);
		assertThat(((Number) run.get("rows_failed")).longValue()).isEqualTo(5);
	}

}
