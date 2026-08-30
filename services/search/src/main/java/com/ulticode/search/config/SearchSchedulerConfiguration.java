package com.ulticode.search.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import java.util.concurrent.RejectedExecutionException;

@Configuration
public class SearchSchedulerConfiguration {

    @Bean(name = "searchConsumeScheduler")
    public ThreadPoolTaskScheduler searchConsumeScheduler(
            @Value("${search.worker.scheduler.consume-pool-size:1}") int poolSize,
            MeterRegistry meterRegistry) {
        return create("search-consume", poolSize, meterRegistry);
    }

    @Bean(name = "searchHeartbeatScheduler")
    public ThreadPoolTaskScheduler searchHeartbeatScheduler(
            @Value("${search.worker.scheduler.heartbeat-pool-size:1}") int poolSize,
            MeterRegistry meterRegistry) {
        return create("search-heartbeat", poolSize, meterRegistry);
    }

    private static ThreadPoolTaskScheduler create(
            String schedulerName, int poolSize, MeterRegistry meterRegistry) {
        if (poolSize < 1 || poolSize > 16) {
            throw new IllegalArgumentException(
                    "scheduler pool size must be between 1 and 16: " + schedulerName);
        }
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        var rejected = io.micrometer.core.instrument.Counter.builder("ulticode.scheduler.rejected")
                .tag("scheduler", schedulerName)
                .description("Scheduled tasks rejected after saturation or shutdown")
                .register(meterRegistry);
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix(schedulerName + "-");
        scheduler.setRejectedExecutionHandler((task, executor) -> {
            rejected.increment();
            throw new RejectedExecutionException("scheduler rejected task: " + schedulerName);
        });
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.setAcceptTasksAfterContextClose(false);
        scheduler.initialize();
        ExecutorServiceMetrics.monitor(
                meterRegistry,
                scheduler.getScheduledThreadPoolExecutor(),
                "ulticode.scheduler",
                Tags.of("scheduler", schedulerName));
        return scheduler;
    }
}
