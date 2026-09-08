package com.example.dashboard.api;

public record RunStatusResponse(long id, String status, String startedAt, String finishedAt, long rowsStaged,
		long rowsLoaded, long rowsFailed) {

}
