package com.example.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dashboard.mart.MartRepo;
import com.example.dashboard.source.SourceProduct;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

class OutOfOrderTest extends AbstractIntegrationTest {

	@Autowired
	private MartRepo martRepo;

	@Autowired
	@Qualifier("martJdbcTemplate")
	private JdbcTemplate mart;

	@BeforeEach
	void clean() {
		mart.update("TRUNCATE products_mart");
	}

	private SourceProduct product(String name, String price, String updatedAt) {
		return new SourceProduct("SKU-1", name, new BigDecimal(price), Instant.parse(updatedAt));
	}

	@Test
	void should_not_overwrite_newer_version_when_older_arrives() {
		martRepo.upsertAll(List.of(product("New", "20.00", "2024-01-02T00:00:00Z")));
		martRepo.upsertAll(List.of(product("Old", "10.00", "2024-01-01T00:00:00Z")));

		var row = mart.queryForMap("SELECT name, price FROM products_mart WHERE sku = 'SKU-1'");
		assertThat(row.get("name")).isEqualTo("New");
		assertThat(row.get("price").toString()).isEqualTo("20.00");
	}

	@Test
	void should_be_noop_when_same_version_repeated() {
		martRepo.upsertAll(List.of(product("Original", "20.00", "2024-01-02T00:00:00Z")));
		// та же версия (равный updated_at), но другой payload — не должна примениться
		martRepo.upsertAll(List.of(product("Duplicate", "99.00", "2024-01-02T00:00:00Z")));

		var row = mart.queryForMap("SELECT name, price FROM products_mart WHERE sku = 'SKU-1'");
		assertThat(row.get("name")).isEqualTo("Original");
		assertThat(row.get("price").toString()).isEqualTo("20.00");
	}

}
