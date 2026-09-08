package com.example.dashboard.mart;

import com.example.dashboard.source.SourceProduct;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StagingRepo {

	private final JdbcTemplate jdbc;

	public StagingRepo(@Qualifier("martJdbcTemplate") JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void insertAll(long runId, List<SourceProduct> batch) {
		jdbc.batchUpdate("INSERT INTO staging_raw (run_id, sku, payload) VALUES (?, ?, ?::jsonb)", batch, batch.size(),
				(ps, product) -> {
					ps.setLong(1, runId);
					ps.setString(2, product.sku());
					ps.setString(3, toJson(product));
				});
	}

	static String toJson(SourceProduct product) {
		return "{\"sku\":" + quote(product.sku()) + ",\"name\":" + quote(product.name()) + ",\"price\":"
				+ product.price() + ",\"updated_at\":\"" + product.updatedAt() + "\"}";
	}

	private static String quote(String value) {
		if (value == null) {
			return "null";
		}
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

}
