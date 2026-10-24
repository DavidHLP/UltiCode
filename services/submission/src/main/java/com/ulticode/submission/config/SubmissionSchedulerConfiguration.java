package com.ulticode.submission.config;

import static com.ulticode.observability.scheduling.SchedulerFactory.create;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SubmissionSchedulerConfiguration {

    @Bean(name = "submissionJudgeOutboxScheduler")
    public ThreadPoolTaskScheduler submissionJudgeOutboxScheduler(
            @Value("${submission.scheduler.judge-outbox-pool-size:1}") int poolSize,
            MeterRegistry meterRegistry) {
        return create("submission-judge-outbox", poolSize, meterRegistry);
    }

    @Bean(name = "submissionResultOutboxScheduler")
    public ThreadPoolTaskScheduler submissionResultOutboxScheduler(
            @Value("${submission.scheduler.result-outbox-pool-size:1}") int poolSize,
            MeterRegistry meterRegistry) {
        return create("submission-result-outbox", poolSize, meterRegistry);
    }

    @Bean(name = "submissionCreatedOutboxScheduler")
    public ThreadPoolTaskScheduler submissionCreatedOutboxScheduler(
            @Value("${submission.scheduler.created-outbox-pool-size:1}") int poolSize,
            MeterRegistry meterRegistry) {
        return create("submission-created-outbox", poolSize, meterRegistry);
    }

    @Bean(name = "submissionLeaseRecoveryScheduler")
    public ThreadPoolTaskScheduler submissionLeaseRecoveryScheduler(
            @Value("${submission.scheduler.lease-recovery-pool-size:1}") int poolSize,
            MeterRegistry meterRegistry) {
        return create("submission-lease-recovery", poolSize, meterRegistry);
    }

}
