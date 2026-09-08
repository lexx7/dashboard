package com.example.dashboard.pipeline;

public class RunConflictException extends RuntimeException {

	public RunConflictException(String pipeline) {
		super("Прогон уже выполняется: pipeline=" + pipeline);
	}

}
