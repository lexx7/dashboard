package com.example.dashboard.pipeline;

import com.example.dashboard.mart.CheckpointRepo;
import com.example.dashboard.mart.MartLoader;
import com.example.dashboard.mart.RunRepo;
import com.example.dashboard.source.SourceProduct;
import com.example.dashboard.source.SourceReader;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

@Service
public class EtlPipeline implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(EtlPipeline.class);

	private final SourceReader sourceReader;

	private final MartLoader martLoader;

	private final CheckpointRepo checkpointRepo;

	private final RunRepo runRepo;

	private final String pipeline;

	private final int batchSize;

	public EtlPipeline(SourceReader sourceReader, MartLoader martLoader, CheckpointRepo checkpointRepo, RunRepo runRepo,
			@Value("${etl.pipeline}") String pipeline, @Value("${etl.batch-size}") int batchSize) {
		this.sourceReader = sourceReader;
		this.martLoader = martLoader;
		this.checkpointRepo = checkpointRepo;
		this.runRepo = runRepo;
		this.pipeline = pipeline;
		this.batchSize = batchSize;
	}

	@Override
	public void run(ApplicationArguments args) {
		int orphaned = runRepo.failOrphanedRuns(pipeline);
		if (orphaned > 0) {
			log.warn("Marked {} orphaned RUNNING runs as FAILED: pipeline={}", orphaned, pipeline);
		}
	}

	public long run() {
		long runId = runRepo.start(pipeline);
		log.info("ETL run started: runId={}, pipeline={}", runId, pipeline);
		try {
			var checkpoint = checkpointRepo.get(pipeline);
			while (true) {
				if (Thread.currentThread().isInterrupted()) {
					throw new InterruptedException("ETL run interrupted between batches");
				}
				List<SourceProduct> batch = sourceReader.fetchSince(checkpoint.lastCursor(), checkpoint.lastSku(),
						batchSize);
				if (batch.isEmpty()) {
					break;
				}
				martLoader.loadBatch(runId, batch);
				SourceProduct last = batch.getLast();
				checkpoint = new CheckpointRepo.Checkpoint(last.updatedAt(), last.sku());
			}
			runRepo.finish(runId, "SUCCESS");
			log.info("ETL run finished: runId={}, status=SUCCESS", runId);
			return runId;
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			runRepo.finish(runId, "FAILED");
			log.warn("ETL run interrupted: runId={}", runId);
			throw new IllegalStateException("ETL run " + runId + " interrupted", e);
		}
		catch (Exception e) {
			runRepo.finish(runId, "FAILED");
			log.error("ETL run failed: runId={}", runId, e);
			throw e;
		}
	}

}
