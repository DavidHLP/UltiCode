package com.ulticode.modules.queue.processor;

import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.queue.job.JudgeJob;
import com.ulticode.modules.queue.pipeline.JudgeExecutionPipeline;
import com.ulticode.modules.queue.pipeline.JudgeExecutionResult;
import com.ulticode.modules.queue.port.JudgeJobHandle;
import com.ulticode.modules.queue.port.JudgeQueue;
import com.ulticode.modules.queue.port.SubmissionResultPushPort;
import com.ulticode.app.api.service.JudgeFeatureFlagsPort;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.app.api.service.ContestSubmissionPort;
import com.ulticode.app.api.service.SubmissionFencePort;
import com.ulticode.app.api.service.SubmissionWritePort;
import com.ulticode.modules.websocket.contest.dto.SubmissionResultPayload;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.Optional;
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

    private final SubmissionWritePort submissionWritePort;
    private final SubmissionResultPushPort submissionResultPushPort;
    private final ContestSubmissionPort contestSubmissionPort;
    private final JudgeExecutionPipeline executionPipeline;
    private final SubmissionFencePort submissionFencePort;
    private final JudgeFeatureFlagsPort featureFlags;
    private final MeterRegistry meterRegistry;
    private final UuidGenerator uuidGenerator;

    /**
     * Single-thread heartbeat scheduler. Lazily initialised because the
     * fenced path is flag-gated and most deployments run flag-off.
     */
    private volatile ScheduledExecutorService heartbeatExecutor;

    public DefaultJudgeAttemptExecutor(SubmissionWritePort submissionWritePort,
                                       SubmissionResultPushPort submissionResultPushPort,
                                       ContestSubmissionPort contestSubmissionPort,
                                       JudgeExecutionPipeline executionPipeline,
                                       SubmissionFencePort submissionFencePort,
                                       JudgeFeatureFlagsPort featureFlags,
                                       MeterRegistry meterRegistry,
                                       UuidGenerator uuidGenerator) {
        this.submissionWritePort = submissionWritePort;
        this.submissionResultPushPort = submissionResultPushPort;
        this.contestSubmissionPort = contestSubmissionPort;
        this.executionPipeline = executionPipeline;
        this.submissionFencePort = submissionFencePort;
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
        markSystemError(submissionId, userId, problemId, contestSubmissionPort.findContestId(submissionId));
    }

    // -----------------------------------------------------------------------
    // Legacy path (no lease CAS)
    // -----------------------------------------------------------------------

    private void processJobLegacy(JudgeJob job, JudgeQueue port, JudgeJobHandle handle) {
        String submissionId = job.getSubmissionId();
        String problemId = job.getProblemId();
        String userId = job.getUserId();

        try {
            transitionToJudging(submissionId);

            Optional<JudgeExecutionResult> result = runPipeline(job);
            if (result.isEmpty()) {
                markSystemError(submissionId, userId, problemId, null);
                releaseIfLeased(port, handle);
                return;
            }
            applyLegacyVerdict(submissionId, userId, problemId, result.get());
            releaseIfLeased(port, handle);
        } catch (Exception e) {
            log.error("Failed to process judge job for submission {}", submissionId, e);
            markSystemError(submissionId, userId, problemId, contestSubmissionPort.findContestId(submissionId));
            releaseIfLeased(port, handle);
        }
    }

    /**
     * Legacy transition step of the attempt lifecycle: flip the submission row
     * to JUDGING before the sandbox runs. Centralised so the verdict constant
     * lives here and the path reads as orchestrator above it.
     */
    private void transitionToJudging(String submissionId) {
        submissionWritePort.updateSubmissionResult(submissionId, SubmissionStatus.JUDGING, 0, 0.0, null);
    }

    /**
     * Legacy verdict step: write the pipeline outcome to the submission row
     * and notify. Cleanup (release) is the caller's responsibility so this
     * helper stays a single-responsibility writer.
     */
    private void applyLegacyVerdict(String submissionId, String userId, String problemId,
                                    JudgeExecutionResult pipelineResult) {
        SubmissionStatus status = pipelineResult.status();
        submissionWritePort.updateSubmissionResult(submissionId, status,
                pipelineResult.maxRuntimeMs(), pipelineResult.maxMemoryMb(), null);
        notifyVerdict(userId, submissionId, problemId, status, pipelineResult);
    }

    // -----------------------------------------------------------------------
    // Fenced path (lease CAS + heartbeat)
    // -----------------------------------------------------------------------

    private void processJobFenced(JudgeJob job, JudgeQueue port, JudgeJobHandle handle) {
        String submissionId = job.getSubmissionId();
        String problemId = job.getProblemId();
        String userId = job.getUserId();
        String attemptId = uuidGenerator.newId();

        Optional<Long> observed = submissionFencePort.currentGeneration(submissionId);
        if (observed.isEmpty()) {
            log.warn("Fenced judge: submission {} not found, abandoning", submissionId);
            releaseIfLeased(port, handle);
            return;
        }
        long generation = observed.get();

        boolean acquired = submissionFencePort.acquireLease(submissionId, generation);
        if (!acquired) {
            log.debug("Fenced judge: lease not acquired for submission {} gen {} (already moved)",
                    submissionId, generation);
            releaseIfLeased(port, handle);
            return;
        }

        ScheduledFuture<?> heartbeatTask = startHeartbeat(submissionId, attemptId);
        try {
            executeAndWriteFenced(job, attemptId, generation);
        } finally {
            stopHeartbeat(heartbeatTask);
            releaseIfLeased(port, handle);
        }
    }

    private void executeAndWriteFenced(JudgeJob job, String attemptId, long generation) {
        String submissionId = job.getSubmissionId();
        String problemId = job.getProblemId();
        String userId = job.getUserId();
        try {
            Optional<JudgeExecutionResult> result = runPipeline(job);
            if (result.isEmpty()) {
                markSystemErrorFenced(submissionId, generation, attemptId, userId, problemId, null);
                return;
            }
            writeVerdictFenced(submissionId, userId, problemId, attemptId, generation, result.get());
        } catch (Exception e) {
            log.error("Failed to process fenced judge job for submission {}", submissionId, e);
            markSystemErrorFenced(submissionId, generation, attemptId, userId, problemId,
                    contestSubmissionPort.findContestId(submissionId));
        }
    }

    /**
     * Fenced verdict step: CAS-write the pipeline outcome and notify only when
     * the CAS lands. Fail-closed conditional fencing lives here as one named
     * seam so the path reads as orchestration above it.
     */
    private void writeVerdictFenced(String submissionId, String userId, String problemId,
                                    String attemptId, long generation,
                                    JudgeExecutionResult pipelineResult) {
        SubmissionStatus status = pipelineResult.status();
        submissionWritePort.updateSubmissionResultFenced(
                submissionId, status,
                pipelineResult.maxRuntimeMs(), pipelineResult.maxMemoryMb(), null,
                generation, Integer.parseInt(attemptId));

        notifyVerdict(userId, submissionId, problemId, status, pipelineResult);
    }

    // -----------------------------------------------------------------------
    // Lease heartbeat
    // -----------------------------------------------------------------------

    private ScheduledFuture<?> startHeartbeat(String submissionId, String attemptId) {
        ScheduledExecutorService executor = getOrCreateHeartbeatExecutor();
        return executor.scheduleAtFixedRate(
                () -> {
                    try {
                        boolean renewed = submissionFencePort.renewLease(submissionId, 0);
                        if (!renewed) {
                            incrementLeaseMissRenew();
                            log.debug("Heartbeat renew failed for submission {} attempt {} (lease lost)",
                                    submissionId, attemptId);
                        }
                    } catch (Exception e) {
                        log.warn("Heartbeat renew threw for submission {}: {}", submissionId, e.getMessage());
                    }
                },
                5000,
                5000,
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

    /**
     * Shared execute step of the attempt lifecycle: run the sandbox pipeline
     * for one job and lift the null-result (sandbox yielded no verdict) into
     * an empty Optional so each path applies its own SYSTEM_ERROR variant.
     */
    private Optional<JudgeExecutionResult> runPipeline(JudgeJob job) throws Exception {
        return Optional.ofNullable(executionPipeline.execute(
                job.getLanguage(), job.getCode(),
                Long.parseLong(job.getProblemId()), job.getUserId(), job.getSubmissionId()));
    }

    /**
     * Shared notify step of the attempt lifecycle: compute the contest-id +
     * memory shape and push one verdict payload. The legacy and fenced
     * success paths share this verbatim; the fenced path gates the call on
     * its CAS landing, keeping the fail-closed conditional fencing intact.
     */
    private void notifyVerdict(String userId, String submissionId, String problemId,
                               SubmissionStatus status, JudgeExecutionResult result) {
        long memoryBytes = (long) (result.maxMemoryMb() * 1024 * 1024);
        String contestId = contestSubmissionPort.findContestId(submissionId);
        pushResult(userId, submissionId, problemId, status.wireValue(),
                result.maxRuntimeMs(), memoryBytes, contestId);
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

    /**
     * Centralised SYSTEM_ERROR write + push for the unfenced (flag-off) path.
     * Collapses the three legacy error branches (markExhausted, null result,
     * pipeline exception) onto one locality so the verdict constant lives here.
     */
    private void markSystemError(String submissionId, String userId, String problemId,
                                 String contestId) {
        SubmissionStatus status = SubmissionStatus.SYSTEM_ERROR;
        submissionWritePort.updateSubmissionResult(submissionId, status, 0, 0.0, null);
        pushResult(userId, submissionId, problemId, status.wireValue(), 0, 0L, contestId);
    }

    /**
     * Centralised fenced SYSTEM_ERROR write + push. The push fires only when the
     * fenced CAS actually lands (written == true), matching the per-branch guard
     * each call site previously inlined.
     */
    private void markSystemErrorFenced(String submissionId, long generation, String attemptId,
                                       String userId, String problemId, String contestId) {
        SubmissionStatus status = SubmissionStatus.SYSTEM_ERROR;
        submissionWritePort.updateSubmissionResultFenced(
                submissionId, status, 0, 0.0, null,
                generation, Integer.parseInt(attemptId));
        pushResult(userId, submissionId, problemId, status.wireValue(), 0, 0L, contestId);
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
