package com.example.dashboard.mart;

import java.time.Instant;

public record RunView(long id, String status, Instant startedAt, Instant finishedAt, long rowsStaged, long rowsLoaded,
		long rowsFailed) {

}
