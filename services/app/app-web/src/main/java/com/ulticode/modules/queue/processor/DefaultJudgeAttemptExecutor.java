package com.ulticode.modules.queue.processor;

import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.queue.job.JudgeJob;
import com.ulticode.modules.queue.pipeline.JudgeExecutionPipeline;
import com.ulticode.modules.queue.pipeline.JudgeExecutionResult;
import com.ulticode.modules.queue.port.JudgeJobHandle;
import com.ulticode.modules.queue.port.JudgeQueue;
import com.ulticode.app.api.service.JudgeFeatureFlagsPort;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.app.api.service.SubmissionFencePort;
import com.ulticode.app.api.service.SubmissionWritePort;
import com.ulticode.modules.submission.codec.TestCaseDetailCodec;
import com.ulticode.modules.submission.fence.LeaseConstants;
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
import java.util.concurrent.atomic.AtomicReference;
/**
 * Default {@link JudgeAttemptExecutor} implementation. Owns the
 * {@code claim → heartbeat → execute → verdict → release} ordering for one
 * judge attempt and the cutover between the legacy and fenced paths.
 *
 * @author ulticode
 */
@Slf4j
@Component
@org.springframework.context.annotation.Profile("!test")
public class DefaultJudgeAttemptExecutor implements JudgeAttemptExecutor {

    private final SubmissionWritePort submissionWritePort;
    private final JudgeExecutionPipeline executionPipeline;
    private final SubmissionFencePort submissionFencePort;
    private final JudgeFeatureFlagsPort featureFlags;
    private final MeterRegistry meterRegistry;
    private final UuidGenerator uuidGenerator;

    private volatile ScheduledExecutorService heartbeatExecutor;

    public DefaultJudgeAttemptExecutor(SubmissionWritePort submissionWritePort,
                                       JudgeExecutionPipeline executionPipeline,
                                       SubmissionFencePort submissionFencePort,
                                       JudgeFeatureFlagsPort featureFlags,
                                       MeterRegistry meterRegistry,
                                       UuidGenerator uuidGenerator) {
        this.submissionWritePort = submissionWritePort;
        this.executionPipeline = executionPipeline;
        this.submissionFencePort = submissionFencePort;
        this.featureFlags = featureFlags;
        this.meterRegistry = meterRegistry;
        this.uuidGenerator = uuidGenerator;
    }

    @Override
    public void runAttempt(JudgeJob job, JudgeQueue port, JudgeJobHandle handle) {
        if (featureFlags != null && featureFlags.isUseGenerationFence()) {
            processJobFenced(job, port, handle);
        } else {
            processJobLegacy(job, port, handle);
        }
    }

    @Override
    public void markExhausted(JudgeJob job) {
        markSystemError(job.getSubmissionId());
    }

    // -----------------------------------------------------------------------
    // Legacy path (no lease CAS)
    // -----------------------------------------------------------------------

    private void processJobLegacy(JudgeJob job, JudgeQueue port, JudgeJobHandle handle) {
        String submissionId = job.getSubmissionId();

        try {
            transitionToJudging(submissionId);

            Optional<JudgeExecutionResult> result = runPipeline(job);
            if (result.isEmpty()) {
                markSystemError(submissionId);
                releaseIfLeased(port, handle);
                return;
            }
            applyLegacyVerdict(submissionId, result.get());
            releaseIfLeased(port, handle);
        } catch (Exception e) {
            log.error("Failed to process judge job for submission {}", submissionId, e);
            markSystemError(submissionId);
            releaseIfLeased(port, handle);
        }
    }

    private void transitionToJudging(String submissionId) {
        submissionWritePort.updateSubmissionResult(submissionId, SubmissionStatus.JUDGING, 0, 0.0, null);
    }

    private void applyLegacyVerdict(String submissionId, JudgeExecutionResult pipelineResult) {
        SubmissionStatus status = pipelineResult.status();
        String testDetailsJson = TestCaseDetailCodec.toJson(pipelineResult.testCaseDetails());
        submissionWritePort.updateSubmissionResult(submissionId, status,
                pipelineResult.maxRuntimeMs(), pipelineResult.maxMemoryMb(), testDetailsJson);
    }

    // -----------------------------------------------------------------------
    // Fenced path (lease CAS + heartbeat)
    // -----------------------------------------------------------------------

    private void processJobFenced(JudgeJob job, JudgeQueue port, JudgeJobHandle handle) {
        String submissionId = job.getSubmissionId();
        String attemptId = uuidGenerator.newId();

        Optional<Long> observed = submissionFencePort.currentGeneration(submissionId);
        if (observed.isEmpty()) {
            log.warn("Fenced judge: submission {} not found, abandoning", submissionId);
            releaseIfLeased(port, handle);
            return;
        }
        long generation = observed.get();

        boolean acquired = submissionFencePort.acquireLease(
                submissionId, attemptId, generation, LeaseConstants.LEASE_TTL_SECONDS);
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
        try {
            Optional<JudgeExecutionResult> result = runPipeline(job);
            if (result.isEmpty()) {
                markSystemErrorFenced(submissionId, generation, attemptId);
                return;
            }
            writeVerdictFenced(submissionId, attemptId, generation, result.get());
        } catch (Exception e) {
            log.error("Failed to process fenced judge job for submission {}", submissionId, e);
            markSystemErrorFenced(submissionId, generation, attemptId);
        }
    }

    private void writeVerdictFenced(String submissionId, String attemptId, long generation,
                                    JudgeExecutionResult pipelineResult) {
        SubmissionStatus status = pipelineResult.status();
        String testDetailsJson = TestCaseDetailCodec.toJson(pipelineResult.testCaseDetails());
        boolean written = submissionWritePort.updateSubmissionResultFenced(
                submissionId, status,
                pipelineResult.maxRuntimeMs(), pipelineResult.maxMemoryMb(), testDetailsJson,
                generation, attemptId);

        if (!written) {
            log.info("Fenced judge: verdict {} for submission {} gen {} dropped (superseded)",
                    status.wireValue(), submissionId, generation);
        }
    }


    // -----------------------------------------------------------------------
    // Lease heartbeat
    // -----------------------------------------------------------------------

    private ScheduledFuture<?> startHeartbeat(String submissionId, String attemptId) {
        ScheduledExecutorService executor = getOrCreateHeartbeatExecutor();
        AtomicReference<ScheduledFuture<?>> taskRef = new AtomicReference<>();
        ScheduledFuture<?> task = executor.scheduleAtFixedRate(
                () -> {
                    try {
                        boolean renewed = submissionFencePort.renewLease(
                                submissionId, attemptId, LeaseConstants.LEASE_TTL_SECONDS);
                        if (!renewed) {
                            incrementLeaseMissRenew();
                            ScheduledFuture<?> scheduledTask = taskRef.get();
                            if (scheduledTask != null) {
                                scheduledTask.cancel(false);
                            }
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
        taskRef.set(task);
        return task;
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

    private Optional<JudgeExecutionResult> runPipeline(JudgeJob job) throws Exception {
        return Optional.ofNullable(executionPipeline.execute(
                job.getLanguage(), job.getCode(),
                Long.parseLong(job.getProblemId()), job.getUserId(), job.getSubmissionId()));
    }


    private void markSystemError(String submissionId) {
        submissionWritePort.updateSubmissionResult(
                submissionId, SubmissionStatus.SYSTEM_ERROR, 0, 0.0, null);
    }

    private void markSystemErrorFenced(String submissionId, long generation, String attemptId) {
        submissionWritePort.updateSubmissionResultFenced(
                submissionId, SubmissionStatus.SYSTEM_ERROR, 0, 0.0, null,
                generation, attemptId);
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
