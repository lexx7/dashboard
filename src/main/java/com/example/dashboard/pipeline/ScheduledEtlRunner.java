package com.example.dashboard.pipeline;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "etl.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledEtlRunner implements SchedulingConfigurer {

	private static final Logger log = LoggerFactory.getLogger(ScheduledEtlRunner.class);

	private final EtlPipeline pipeline;

	private final Duration pollInterval;

	public ScheduledEtlRunner(EtlPipeline pipeline, @Value("${etl.poll-interval}") Duration pollInterval) {
		this.pipeline = pipeline;
		this.pollInterval = pollInterval;
	}

	@Override
	public void configureTasks(ScheduledTaskRegistrar registrar) {
		registrar.addFixedDelayTask(() -> {
			try {
				pipeline.run();
			}
			catch (RunConflictException e) {
				log.debug("Scheduled run skipped: another run is active");
			}
		}, pollInterval);
	}

}
