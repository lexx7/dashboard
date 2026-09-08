package com.example.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dashboard.pipeline.EtlPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class IncrementalSyncTest extends AbstractIntegrationTest {

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

	private void insertSource(String sku, String name, String price) {
		sourceWriter.update(
				"INSERT INTO products (sku, name, price, updated_at) VALUES (?, ?, ?::numeric, now() - interval '1 minute')",
				sku, name, price);
	}

	private long martCount() {
		return mart.queryForObject("SELECT count(*) FROM products_mart", Long.class);
	}

	@Test
	void should_transfer_new_records_when_source_has_changes() {
		insertSource("SKU-1", "Widget", "10.00");
		insertSource("SKU-2", "Gadget", "20.50");
		insertSource("SKU-3", "Sprocket", "5.25");

		long runId = pipeline.run();

		assertThat(martCount()).isEqualTo(3);
		var row = mart.queryForMap("SELECT name, price FROM products_mart WHERE sku = 'SKU-2'");
		assertThat(row.get("name")).isEqualTo("Gadget");
		assertThat(row.get("price").toString()).isEqualTo("20.50");

		var run = mart.queryForMap("SELECT status, rows_staged, rows_loaded, rows_failed FROM etl_run WHERE id = ?", runId);
		assertThat(run.get("status")).isEqualTo("SUCCESS");
		assertThat(((Number) run.get("rows_staged")).longValue()).isEqualTo(3);
		assertThat(((Number) run.get("rows_loaded")).longValue()).isEqualTo(3);
		assertThat(((Number) run.get("rows_failed")).longValue()).isZero();
	}

	@Test
	void should_transfer_updates_when_record_changes() {
		insertSource("SKU-1", "Widget", "10.00");
		pipeline.run();

		sourceWriter.update(
				"UPDATE products SET name = 'Widget v2', price = 12.00, updated_at = now() - interval '1 minute' WHERE sku = 'SKU-1'");
		pipeline.run();

		var row = mart.queryForMap("SELECT name, price FROM products_mart WHERE sku = 'SKU-1'");
		assertThat(row.get("name")).isEqualTo("Widget v2");
		assertThat(row.get("price").toString()).isEqualTo("12.00");
		assertThat(martCount()).isEqualTo(1);
	}

	@Test
	void should_load_zero_rows_when_run_is_empty() {
		insertSource("SKU-1", "Widget", "10.00");
		pipeline.run();

		long secondRunId = pipeline.run();

		assertThat(martCount()).isEqualTo(1);
		var run = mart.queryForMap("SELECT rows_staged, rows_loaded FROM etl_run WHERE id = ?", secondRunId);
		assertThat(((Number) run.get("rows_staged")).longValue()).isZero();
		assertThat(((Number) run.get("rows_loaded")).longValue()).isZero();
	}

	@Test
	void should_not_miss_records_with_same_updated_at() {
		for (int i = 0; i < 5; i++) {
			sourceWriter.update(
					"INSERT INTO products (sku, name, price, updated_at) VALUES (?, ?, 1.00, '2020-01-01T00:00:00Z')",
					"SKU-" + i, "Product " + i);
		}

		pipeline.run();

		assertThat(martCount()).isEqualTo(5);
	}

}
