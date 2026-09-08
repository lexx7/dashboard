package com.example.dashboard.mart;

import com.example.dashboard.mart.ErrorRepo.BrokenRecord;
import com.example.dashboard.pipeline.BatchResult;
import com.example.dashboard.source.SourceProduct;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MartLoader {

	private final StagingRepo stagingRepo;

	private final MartRepo martRepo;

	private final ErrorRepo errorRepo;

	private final CheckpointRepo checkpointRepo;

	private final RunRepo runRepo;

	private final String pipeline;

	public MartLoader(StagingRepo stagingRepo, MartRepo martRepo, ErrorRepo errorRepo, CheckpointRepo checkpointRepo,
			RunRepo runRepo, @Value("${etl.pipeline}") String pipeline) {
		this.stagingRepo = stagingRepo;
		this.martRepo = martRepo;
		this.errorRepo = errorRepo;
		this.checkpointRepo = checkpointRepo;
		this.runRepo = runRepo;
		this.pipeline = pipeline;
	}

	@Transactional("martTransactionManager")
	public BatchResult loadBatch(long runId, List<SourceProduct> batch) {
		stagingRepo.insertAll(runId, batch);
		var valid = new ArrayList<SourceProduct>();
		var broken = new ArrayList<BrokenRecord>();
		for (SourceProduct product : batch) {
			String reason = invalidReason(product);
			if (reason == null) {
				valid.add(product);
			}
			else {
				broken.add(new BrokenRecord(product, reason));
			}
		}
		errorRepo.insertAll(runId, broken);
		martRepo.upsertAll(valid);
		SourceProduct last = batch.getLast();
		checkpointRepo.advance(pipeline, last.updatedAt(), last.sku());
		runRepo.addStats(runId, batch.size(), valid.size(), broken.size());
		return new BatchResult(valid.size(), broken.size());
	}

	private String invalidReason(SourceProduct product) {
		if (product.sku() == null || product.sku().isBlank() || product.name() == null || product.price() == null) {
			return "missing required field";
		}
		if (product.price().signum() < 0) {
			return "negative price";
		}
		return null;
	}

}
