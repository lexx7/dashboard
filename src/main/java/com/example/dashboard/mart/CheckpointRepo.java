package com.example.dashboard.mart;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CheckpointRepo {

	public record Checkpoint(Instant lastCursor, String lastSku) {

	}

	private final JdbcTemplate jdbc;

	public CheckpointRepo(@Qualifier("martJdbcTemplate") JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Checkpoint get(String pipeline) {
		var rows = jdbc.query(
				"SELECT last_cursor, last_sku FROM etl_checkpoint WHERE pipeline = ?",
				(rs, rowNum) -> new Checkpoint(rs.getObject("last_cursor", OffsetDateTime.class).toInstant(),
						rs.getString("last_sku")),
				pipeline);
		return rows.isEmpty() ? new Checkpoint(Instant.EPOCH, "") : rows.getFirst();
	}

	public void advance(String pipeline, Instant cursor, String sku) {
		jdbc.update("""
				INSERT INTO etl_checkpoint (pipeline, last_cursor, last_sku)
				VALUES (?, ?, ?)
				ON CONFLICT (pipeline) DO UPDATE
				SET last_cursor = EXCLUDED.last_cursor, last_sku = EXCLUDED.last_sku
				""", pipeline, Timestamp.from(cursor), sku == null ? "" : sku);
	}

}
