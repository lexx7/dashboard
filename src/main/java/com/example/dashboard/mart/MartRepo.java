package com.example.dashboard.mart;

import com.example.dashboard.source.SourceProduct;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MartRepo {

	private final JdbcTemplate jdbc;

	public MartRepo(@Qualifier("martJdbcTemplate") JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void upsertAll(List<SourceProduct> batch) {
		jdbc.batchUpdate("""
				INSERT INTO products_mart (sku, name, price, updated_at)
				VALUES (?, ?, ?, ?)
				ON CONFLICT (sku) DO UPDATE
				SET name = EXCLUDED.name, price = EXCLUDED.price, updated_at = EXCLUDED.updated_at
				""", batch, batch.size(), (ps, product) -> {
			ps.setString(1, product.sku());
			ps.setString(2, product.name());
			ps.setBigDecimal(3, product.price());
			ps.setTimestamp(4, Timestamp.from(product.updatedAt()));
		});
	}

}
