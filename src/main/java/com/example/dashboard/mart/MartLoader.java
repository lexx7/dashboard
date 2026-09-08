package com.example.dashboard.mart;

import com.example.dashboard.pipeline.BatchResult;
import com.example.dashboard.source.SourceProduct;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MartLoader {

	private final StagingRepo stagingRepo;

	private final MartRepo martRepo;

	private final CheckpointRepo checkpointRepo;

	private final RunRepo runRepo;

	private final String pipeline;

	public MartLoader(StagingRepo stagingRepo, MartRepo martRepo, CheckpointRepo checkpointRepo, RunRepo runRepo,
			@Value("${etl.pipeline}") String pipeline) {
		this.stagingRepo = stagingRepo;
		this.martRepo = martRepo;
		this.checkpointRepo = checkpointRepo;
		this.runRepo = runRepo;
		this.pipeline = pipeline;
	}

	@Transactional("martTransactionManager")
	public BatchResult loadBatch(long runId, List<SourceProduct> batch) {
		stagingRepo.insertAll(runId, batch);
		martRepo.upsertAll(batch);
		SourceProduct last = batch.getLast();
		checkpointRepo.advance(pipeline, last.updatedAt(), last.sku());
		runRepo.addStats(runId, batch.size(), batch.size(), 0);
		return new BatchResult(batch.size(), 0);
	}

}
