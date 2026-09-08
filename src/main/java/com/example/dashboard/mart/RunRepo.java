package com.example.dashboard.mart;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RunRepo {

	private final JdbcTemplate jdbc;

	public RunRepo(@Qualifier("martJdbcTemplate") JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public long start(String pipeline) {
		return jdbc.queryForObject(
				"INSERT INTO etl_run (pipeline, started_at, status) VALUES (?, now(), 'RUNNING') RETURNING id",
				Long.class, pipeline);
	}

	public Optional<Long> tryStart(String pipeline) {
		var ids = jdbc.query("""
				INSERT INTO etl_run (pipeline, started_at, status)
				SELECT ?, now(), 'RUNNING'
				WHERE NOT EXISTS (SELECT 1 FROM etl_run WHERE pipeline = ? AND status = 'RUNNING')
				RETURNING id
				""", (rs, rowNum) -> rs.getLong(1), pipeline, pipeline);
		return ids.stream().findFirst();
	}

	public Optional<RunView> findById(long runId) {
		var rows = jdbc.query("""
				SELECT id, status, started_at, finished_at, rows_staged, rows_loaded, rows_failed
				FROM etl_run WHERE id = ?
				""", (rs, rowNum) -> new RunView(rs.getLong("id"), rs.getString("status"),
				rs.getObject("started_at", OffsetDateTime.class).toInstant(),
				Optional.ofNullable(rs.getObject("finished_at", OffsetDateTime.class))
					.map(OffsetDateTime::toInstant)
					.orElse(null),
				rs.getLong("rows_staged"), rs.getLong("rows_loaded"), rs.getLong("rows_failed")), runId);
		return rows.stream().findFirst();
	}

	public void finish(long runId, String status) {
		jdbc.update("UPDATE etl_run SET finished_at = now(), status = ? WHERE id = ?", status, runId);
	}

	public int failOrphanedRuns(String pipeline) {
		return jdbc.update("""
				UPDATE etl_run SET status = 'FAILED', finished_at = COALESCE(finished_at, now())
				WHERE pipeline = ? AND status = 'RUNNING'
				""", pipeline);
	}

	public void addStats(long runId, long staged, long loaded, long failed) {
		jdbc.update("""
				UPDATE etl_run
				SET rows_staged = rows_staged + ?, rows_loaded = rows_loaded + ?, rows_failed = rows_failed + ?
				WHERE id = ?
				""", staged, loaded, failed, runId);
	}

}
