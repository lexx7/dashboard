package com.example.dashboard;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class EtlApiTest extends AbstractIntegrationTest {

	private final JdbcTemplate sourceWriter = new JdbcTemplate(
			new DriverManagerDataSource(SOURCE_DB.getJdbcUrl(), SOURCE_DB.getUsername(), SOURCE_DB.getPassword()));

	@Autowired
	@Qualifier("martJdbcTemplate")
	private JdbcTemplate mart;

	@Autowired
	private MockMvc mockMvc;

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

	private String runStatus(long runId) {
		return mart.queryForObject("SELECT status FROM etl_run WHERE id = ?", String.class, runId);
	}

	@Test
	void should_start_run_and_report_status() throws Exception {
		insertSource("SKU-1", "Widget", "10.00");
		insertSource("SKU-2", "Gadget", "20.50");

		var response = mockMvc.perform(post("/api/etl/run"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.runId").isNumber())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long runId = Long.parseLong(response.replaceAll("\\D+", ""));

		await().atMost(Duration.ofSeconds(30)).until(() -> "SUCCESS".equals(runStatus(runId)));

		mockMvc.perform(get("/api/etl/run/{id}", runId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(runId))
			.andExpect(jsonPath("$.status").value("SUCCESS"))
			.andExpect(jsonPath("$.rowsStaged").value(2))
			.andExpect(jsonPath("$.rowsLoaded").value(2))
			.andExpect(jsonPath("$.rowsFailed").value(0));
	}

	@Test
	void should_return_409_when_run_already_active() throws Exception {
		mart.update("INSERT INTO etl_run (pipeline, started_at, status) VALUES ('catalog-sync', now(), 'RUNNING')");

		mockMvc.perform(post("/api/etl/run")).andExpect(status().isConflict());
	}

	@Test
	void should_return_404_when_run_id_unknown() throws Exception {
		mockMvc.perform(get("/api/etl/run/{id}", 999_999)).andExpect(status().isNotFound());
	}

}
