package com.example.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dashboard.pipeline.EtlPipeline;
import com.example.dashboard.reconcile.Reconciler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ReconciliationTest extends AbstractIntegrationTest {

	private final JdbcTemplate sourceWriter = new JdbcTemplate(
			new DriverManagerDataSource(SOURCE_DB.getJdbcUrl(), SOURCE_DB.getUsername(), SOURCE_DB.getPassword()));

	@Autowired
	@Qualifier("martJdbcTemplate")
	private JdbcTemplate mart;

	@Autowired
	private EtlPipeline pipeline;

	@Autowired
	private Reconciler reconciler;

	@BeforeEach
	void clean() {
		sourceWriter.update("DELETE FROM products");
		mart.update("TRUNCATE staging_raw, etl_error, products_mart, etl_checkpoint, etl_run CASCADE");
	}

	@Test
	void should_report_mismatch_when_records_are_missing_in_mart() {
		sourceWriter.update("""
				INSERT INTO products (sku, name, price, updated_at)
				SELECT 'SKU-' || g, 'Product ' || g, 1.00, now() - interval '1 minute'
				FROM generate_series(1, 500) g
				""");
		pipeline.run();
		mart.update("DELETE FROM products_mart WHERE sku IN (SELECT sku FROM products_mart ORDER BY sku LIMIT 100)");

		var report = reconciler.reconcile();

		assertThat(report.sourceCount()).isEqualTo(500);
		assertThat(report.martCount()).isEqualTo(400);
		assertThat(report.mismatch()).isEqualTo(100);
		assertThat(report.details()).contains("source=500").contains("mart=400");
	}

}
