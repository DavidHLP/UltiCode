package com.ulticode.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Dedicated thread pool for {@code @Scheduled} tasks.
 *
 * <p>Spring Boot defaults to a <b>single-thread</b> scheduler for all
 * {@code @Scheduled} methods. ADR-003 M3a/M3b adds three new scheduled
 * components (outbox dispatcher 2s, lease reaper 5s, shadow comparator 5s) on
 * top of the existing JudgeWorkerProcessor (1s poll), BackupScheduler, and
 * ContestScheduler. Running all of these on one thread would serialize them —
 * a slow lease-reaper sweep would stall the 1s judge poll and vice versa.
 *
 * <p>This config installs a {@link ThreadPoolTaskScheduler} with a pool sized
 * to the number of independent scheduled duties so each can make progress
 * independently. The scheduler uses named threads for diagnostics.
 */
@Configuration
public class SchedulingConfig implements SchedulingConfigurer {

    /** Pool size: enough to run the judge poll, reaper, dispatcher, comparator, and existing schedulers concurrently. */
    private static final int POOL_SIZE = 4;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(POOL_SIZE);
        scheduler.setThreadNamePrefix("ulticode-sched-");
        // Keep the JVM responsive to shutdown even if a scheduled task is mid-flight.
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.initialize();
        taskRegistrar.setTaskScheduler(scheduler);
    }
}
