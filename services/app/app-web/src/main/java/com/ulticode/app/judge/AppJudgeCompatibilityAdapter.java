package com.ulticode.app.judge;

import com.ulticode.app.api.service.JudgeFeatureFlagsPort;
import com.ulticode.modules.queue.config.QueueConfig;
import com.ulticode.modules.queue.constants.QueueConstants;
import com.ulticode.modules.queue.job.JudgeJob;
import com.ulticode.modules.queue.processor.JudgeAttemptExecutor;
import com.ulticode.modules.queue.service.QueueService;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Minimal App compatibility adapter retained until the legacy rollback asset
 * is retired. It only polls the old RQueue and delegates execution; all
 * attempt lifecycle, sandbox, fence, and verdict policy stays in the shared
 * deep execution module.
 */
@Slf4j
final class AppJudgeCompatibilityAdapter {

    private final QueueService queueService;
    private final QueueConfig queueConfig;
    private final JudgeFeatureFlagsPort featureFlags;
    private final JudgeAttemptExecutor attemptExecutor;
    private final AtomicInteger activeJobs = new AtomicInteger();

    AppJudgeCompatibilityAdapter(
            QueueService queueService,
            QueueConfig queueConfig,
            JudgeFeatureFlagsPort featureFlags,
            JudgeAttemptExecutor attemptExecutor) {
        this.queueService = queueService;
        this.queueConfig = queueConfig;
        this.featureFlags = featureFlags;
        this.attemptExecutor = attemptExecutor;
    }

    @Scheduled(
            fixedDelayString = "${queue.poll-interval-ms:1000}",
            initialDelayString = "${queue.judge.initial-delay-ms:5000}")
    void pollAndProcess() {
        if (!queueConfig.isJudgeEnabled()
                || featureFlags.isJudgeQueueUsePort()
                || !tryAcquireJobSlot()) {
            return;
        }
        try {
            Object polled = queueService.pollJob(QueueConstants.JUDGE_QUEUE);
            if (polled instanceof JudgeJob job) {
                attemptExecutor.runAttempt(job, null, null);
            }
        } catch (Exception exception) {
            log.error("App Judge compatibility polling failed", exception);
        } finally {
            activeJobs.decrementAndGet();
        }
    }

    private boolean tryAcquireJobSlot() {
        int max = queueConfig.getMaxConcurrentJobs();
        while (max > 0) {
            int current = activeJobs.get();
            if (current >= max || !activeJobs.compareAndSet(current, current + 1)) {
                if (current >= max) {
                    return false;
                }
                continue;
            }
            return true;
        }
        return false;
    }
}
