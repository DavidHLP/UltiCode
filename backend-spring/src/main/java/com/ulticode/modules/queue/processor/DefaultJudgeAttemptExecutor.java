package com.ulticode.modules.queue.processor;

import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.ulticode.modules.queue.job.JudgeJob;
import com.ulticode.modules.queue.pipeline.JudgeExecutionPipeline;
import com.ulticode.modules.queue.pipeline.JudgeExecutionResult;
import com.ulticode.modules.queue.port.JudgeJobHandle;
import com.ulticode.modules.queue.port.JudgeQueue;
import com.ulticode.modules.queue.port.SubmissionResultPushPort;
import com.ulticode.modules.submission.config.FeatureFlagsProperties;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.fence.LeaseConstants;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.service.SubmissionService;
import com.ulticode.modules.websocket.contest.dto.SubmissionResultPayload;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default {@link JudgeAttemptExecutor} implementation. Owns the
 * {@code claim → heartbeat → execute → verdict → contest effect → push → release}
 * ordering for one judge attempt and the cutover between the legacy and
 * fenced paths.
 *
 * <p>Extracted from the previous {@code JudgeWorkerProcessor} (which is now
 * a thin polling adapter). The behaviour of the legacy and fenced paths is
 * preserved verbatim — architecture-review candidate #4.
 *
 * @author ulticode
 */
@Slf4j
@Component
public class DefaultJudgeAttemptExecutor implements JudgeAttemptExecutor {

    private final SubmissionService submissionService;
    private final SubmissionResultPushPort submissionResultPushPort;
    private final ContestSubmissionMapper contestSubmissionMapper;
    private final JudgeExecutionPipeline executionPipeline;
    private final SubmissionMapper submissionMapper;
    private final FeatureFlagsProperties featureFlags;
    private final MeterRegistry meterRegistry;
    private final UuidGenerator uuidGenerator;

    /**
     * Single-thread heartbeat scheduler. Lazily initialised because the
     * fenced path is flag-gated and most deployments run flag-off.
     */
    private volatile ScheduledExecutorService heartbeatExecutor;

    public DefaultJudgeAttemptExecutor(SubmissionService submissionService,
                                       SubmissionResultPushPort submissionResultPushPort,
                                       ContestSubmissionMapper contestSubmissionMapper,
                                       JudgeExecutionPipeline executionPipeline,
                                       SubmissionMapper submissionMapper,
                                       FeatureFlagsProperties featureFlags,
                                       MeterRegistry meterRegistry,
                                       UuidGenerator uuidGenerator) {
        this.submissionService = submissionService;
        this.submissionResultPushPort = submissionResultPushPort;
        this.contestSubmissionMapper = contestSubmissionMapper;
        this.executionPipeline = executionPipeline;
        this.submissionMapper = submissionMapper;
        this.featureFlags = featureFlags;
        this.meterRegistry = meterRegistry;
        this.uuidGenerator = uuidGenerator;
    }

    @Override
    public void runAttempt(JudgeJob job, JudgeQueue port, JudgeJobHandle handle) {
        // When the fenced path is on, every attempt goes through the lease
        // CAS regardless of which queue adapter delivered it. The legacy
        // path is the no-lease fallback for deployments that have not
        // completed the M3c-2 cutover.
        if (featureFlags != null && featureFlags.isUseGenerationFence()) {
            processJobFenced(job, port, handle);
        } else {
            processJobLegacy(job, port, handle);
        }
    }

    @Override
    public void markExhausted(JudgeJob job) {
        String submissionId = job.getSubmissionId();
        String problemId = job.getProblemId();
        String userId = job.getUserId();
        submissionService.updateSubmissionResult(submissionId, "System Error", 0, 0.0, null);
        String failedContestId = findContestIdBySubmissionId(submissionId);
        pushResult(userId, submissionId, problemId, "System Error", 0, 0L, failedContestId);
    }

    // -----------------------------------------------------------------------
    // Legacy path (no lease CAS)
    // -----------------------------------------------------------------------

    private void processJobLegacy(JudgeJob job, JudgeQueue port, JudgeJobHandle handle) {
        String submissionId = job.getSubmissionId();
        String problemId = job.getProblemId();
        String userId = job.getUserId();

        try {
            submissionService.updateSubmissionResult(submissionId, "Judging", 0, null, null);

            JudgeExecutionResult result = executionPipeline.execute(
                    job.getLanguage(), job.getCode(),
                    Long.parseLong(problemId), userId, submissionId);

            if (result == null) {
                submissionService.updateSubmissionResult(submissionId, "System Error", 0, 0.0, null);
                pushResult(userId, submissionId, problemId, "System Error", 0, 0L, null);
                releaseIfLeased(port, handle);
                return;
            }

            submissionService.updateSubmissionResult(submissionId, result.verdict(),
                    result.maxRuntimeMs(), result.maxMemoryMb(), result.testCaseDetails());

            long memoryBytes = (long) (result.maxMemoryMb() * 1024 * 1024);
            String contestId = findContestIdBySubmissionId(submissionId);
            pushResult(userId, submissionId, problemId, result.verdict(),
                    result.maxRuntimeMs(), memoryBytes, contestId);
            releaseIfLeased(port, handle);
        } catch (Exception e) {
            log.error("Failed to process judge job for submission {}", submissionId, e);
            submissionService.updateSubmissionResult(submissionId, "System Error", 0, 0.0, null);
            String failedContestId = findContestIdBySubmissionId(submissionId);
            pushResult(userId, submissionId, problemId, "System Error", 0, 0L, failedContestId);
            releaseIfLeased(port, handle);
        }
    }

    // -----------------------------------------------------------------------
    // Fenced path (lease CAS + heartbeat)
    // -----------------------------------------------------------------------

    private void processJobFenced(JudgeJob job, JudgeQueue port, JudgeJobHandle handle) {
        String submissionId = job.getSubmissionId();
        String problemId = job.getProblemId();
        String userId = job.getUserId();
        String attemptId = uuidGenerator.newId();

        Submission current = submissionMapper.selectById(submissionId);
        if (current == null) {
            log.warn("Fenced judge: submission {} not found, abandoning", submissionId);
            releaseIfLeased(port, handle);
            return;
        }
        long generation = current.getGeneration() != null ? current.getGeneration() : 1L;

        int acquired = submissionMapper.acquireLease(
                submissionId, attemptId, generation, LeaseConstants.LEASE_TTL_SECONDS);
        if (acquired != 1) {
            log.debug("Fenced judge: lease not acquired for submission {} gen {} (already moved)",
                    submissionId, generation);
            releaseIfLeased(port, handle);
            return;
        }

        ScheduledFuture<?> heartbeatTask = startHeartbeat(submissionId, attemptId);
        try {
            executeAndWriteFenced(
                    submissionId, problemId, userId,
                    job.getLanguage(), job.getCode(),
                    attemptId, generation);
        } finally {
            stopHeartbeat(heartbeatTask);
            releaseIfLeased(port, handle);
        }
    }

    private void executeAndWriteFenced(String submissionId, String problemId, String userId,
                                       String language, String code,
                                       String attemptId, long generation) {
        try {
            JudgeExecutionResult result = executionPipeline.execute(
                    language, code, Long.parseLong(problemId), userId, submissionId);

            if (result == null) {
                boolean written = submissionService.updateSubmissionResultFenced(
                        submissionId, generation, attemptId, "System Error", 0, 0.0, null);
                if (written) {
                    pushResult(userId, submissionId, problemId, "System Error", 0, 0L, null);
                }
                return;
            }

            boolean written = submissionService.updateSubmissionResultFenced(
                    submissionId, generation, attemptId, result.verdict(),
                    result.maxRuntimeMs(), result.maxMemoryMb(), result.testCaseDetails());

            if (written) {
                long memoryBytes = (long) (result.maxMemoryMb() * 1024 * 1024);
                String contestId = findContestIdBySubmissionId(submissionId);
                pushResult(userId, submissionId, problemId, result.verdict(),
                        result.maxRuntimeMs(), memoryBytes, contestId);
            } else {
                log.info("Fenced judge: verdict {} for submission {} gen {} dropped (superseded)",
                        result.verdict(), submissionId, generation);
            }
        } catch (Exception e) {
            log.error("Failed to process fenced judge job for submission {}", submissionId, e);
            boolean written = submissionService.updateSubmissionResultFenced(
                    submissionId, generation, attemptId, "System Error", 0, 0.0, null);
            if (written) {
                String failedContestId = findContestIdBySubmissionId(submissionId);
                pushResult(userId, submissionId, problemId, "System Error", 0, 0L, failedContestId);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Lease heartbeat
    // -----------------------------------------------------------------------

    private ScheduledFuture<?> startHeartbeat(String submissionId, String attemptId) {
        ScheduledExecutorService executor = getOrCreateHeartbeatExecutor();
        return executor.scheduleAtFixedRate(
                () -> {
                    try {
                        int renewed = submissionMapper.renewLease(
                                submissionId, attemptId, LeaseConstants.LEASE_TTL_SECONDS);
                        if (renewed != 1) {
                            incrementLeaseMissRenew();
                            log.debug("Heartbeat renew failed for submission {} attempt {} (lease lost)",
                                    submissionId, attemptId);
                        }
                    } catch (Exception e) {
                        log.warn("Heartbeat renew threw for submission {}: {}", submissionId, e.getMessage());
                    }
                },
                LeaseConstants.HEARTBEAT_INTERVAL_MS,
                LeaseConstants.HEARTBEAT_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
    }

    private void stopHeartbeat(ScheduledFuture<?> heartbeatTask) {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
    }

    private ScheduledExecutorService getOrCreateHeartbeatExecutor() {
        ScheduledExecutorService local = heartbeatExecutor;
        if (local == null) {
            synchronized (this) {
                local = heartbeatExecutor;
                if (local == null) {
                    local = new ScheduledThreadPoolExecutor(
                            1,
                            new NamedDaemonThreadFactory("judge-heartbeat"));
                    heartbeatExecutor = local;
                }
            }
        }
        return local;
    }

    private void incrementLeaseMissRenew() {
        if (meterRegistry != null) {
            meterRegistry.counter("judge.lease.miss_renew").increment();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void releaseIfLeased(JudgeQueue port, JudgeJobHandle handle) {
        if (port != null && handle != null) {
            try {
                port.ack(handle);
            } catch (Exception e) {
                log.warn("Failed to ack job handle {} on {}: {}",
                        handle, port.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    private String findContestIdBySubmissionId(String submissionId) {
        try {
            return contestSubmissionMapper.findBySubmissionId(submissionId)
                    .map(cs -> cs.getContestId())
                    .orElse(null);
        } catch (Exception e) {
            log.debug("No contest mapping for submission {}: {}", submissionId, e.getMessage());
            return null;
        }
    }

    private void pushResult(String userId, String submissionId, String problemId,
                            String verdict, int runtimeMs, long memoryBytes,
                            String contestId) {
        try {
            submissionResultPushPort.emitSubmissionResult(
                    userId,
                    SubmissionResultPayload.of(
                            submissionId, contestId, problemId, userId, verdict,
                            0.0, runtimeMs, memoryBytes));
        } catch (Exception e) {
            log.warn("Failed to push judge result for submission {}: {}",
                    submissionId, e.getMessage());
        }
    }

    private static final class NamedDaemonThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger(0);

        NamedDaemonThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}
