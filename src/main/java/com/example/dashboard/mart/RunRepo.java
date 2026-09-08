package com.example.dashboard.mart;

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

	public void finish(long runId, String status) {
		jdbc.update("UPDATE etl_run SET finished_at = now(), status = ? WHERE id = ?", status, runId);
	}

	public void addStats(long runId, long staged, long loaded, long failed) {
		jdbc.update("""
				UPDATE etl_run
				SET rows_staged = rows_staged + ?, rows_loaded = rows_loaded + ?, rows_failed = rows_failed + ?
				WHERE id = ?
				""", staged, loaded, failed, runId);
	}

}
