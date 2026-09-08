package com.example.dashboard.reconcile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "etl.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledReconcileRunner {

	private static final Logger log = LoggerFactory.getLogger(ScheduledReconcileRunner.class);

	private final Reconciler reconciler;

	public ScheduledReconcileRunner(Reconciler reconciler) {
		this.reconciler = reconciler;
	}

	@Scheduled(cron = "${etl.reconcile-cron}")
	public void reconcileNightly() {
		ReconcileReport report = reconciler.reconcile();
		log.info("Reconciliation finished: sourceCount={}, martCount={}, mismatch={}", report.sourceCount(),
				report.martCount(), report.mismatch());
	}

}
