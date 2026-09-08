package com.example.dashboard.api;

import com.example.dashboard.mart.RunRepo;
import com.example.dashboard.mart.RunView;
import com.example.dashboard.pipeline.EtlPipeline;
import com.example.dashboard.pipeline.RunConflictException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/etl")
public class EtlController {

	private final EtlPipeline pipeline;

	private final RunRepo runRepo;

	private final TaskExecutor taskExecutor;

	public EtlController(EtlPipeline pipeline, RunRepo runRepo,
			@Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
		this.pipeline = pipeline;
		this.runRepo = runRepo;
		this.taskExecutor = taskExecutor;
	}

	@PostMapping("/run")
	public Map<String, Long> run() {
		long runId = pipeline.beginRun();
		taskExecutor.execute(() -> pipeline.executeRun(runId));
		return Map.of("runId", runId);
	}

	@GetMapping("/run/{id}")
	public ResponseEntity<RunStatusResponse> status(@PathVariable long id) {
		return runRepo.findById(id)
			.map(this::toResponse)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@ExceptionHandler(RunConflictException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public void conflict() {
	}

	private RunStatusResponse toResponse(RunView run) {
		return new RunStatusResponse(run.id(), run.status(), run.startedAt().toString(),
				run.finishedAt() == null ? null : run.finishedAt().toString(), run.rowsStaged(), run.rowsLoaded(),
				run.rowsFailed());
	}

}
