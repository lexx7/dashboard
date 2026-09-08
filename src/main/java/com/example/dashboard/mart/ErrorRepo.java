package com.example.dashboard.mart;

import com.example.dashboard.source.SourceProduct;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ErrorRepo {

	public record BrokenRecord(SourceProduct product, String reason) {

	}

	private final JdbcTemplate jdbc;

	public ErrorRepo(@Qualifier("martJdbcTemplate") JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void insertAll(long runId, List<BrokenRecord> broken) {
		jdbc.batchUpdate("INSERT INTO etl_error (run_id, sku, payload, reason) VALUES (?, ?, ?::jsonb, ?)", broken,
				broken.size(), (ps, record) -> {
					ps.setLong(1, runId);
					ps.setString(2, record.product().sku());
					ps.setString(3, StagingRepo.toJson(record.product()));
					ps.setString(4, record.reason());
				});
	}

}
