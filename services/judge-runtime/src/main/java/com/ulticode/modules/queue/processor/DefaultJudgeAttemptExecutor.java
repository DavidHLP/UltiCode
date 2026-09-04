package com.ulticode.modules.queue.processor;

import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.queue.job.JudgeJob;
import com.ulticode.modules.queue.pipeline.JudgeExecutionPipeline;
import com.ulticode.modules.queue.pipeline.JudgeExecutionResult;
import com.ulticode.modules.queue.pipeline.JudgeTestCaseDetailCodec;
import com.ulticode.submission.api.queue.JudgeJobHandle;
import com.ulticode.submission.api.queue.JudgeJobEnvelope;
import com.ulticode.submission.api.queue.JudgeQueue;
import com.ulticode.modules.submission.port.JudgeFeatureFlagsPort;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.submission.api.service.SubmissionFencePort;
import com.ulticode.submission.api.service.SubmissionVerdictWritePort;
import com.ulticode.modules.submission.fence.LeaseConstants;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

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
public class DefaultJudgeAttemptExecutor implements JudgeAttemptExecutor {

    private final SubmissionVerdictWritePort submissionWritePort;
    private final JudgeExecutionPipeline executionPipeline;
    private final SubmissionFencePort submissionFencePort;
    private final JudgeFeatureFlagsPort featureFlags;
    private final MeterRegistry meterRegistry;
    private final UuidGenerator uuidGenerator;

    private volatile ScheduledExecutorService heartbeatExecutor;

    public DefaultJudgeAttemptExecutor(SubmissionVerdictWritePort submissionWritePort,
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
        boolean envelopeFence = handle != null
                && handle.envelope() != null
                && handle.envelope().isFenceAware();
        if (envelopeFence || (featureFlags != null && featureFlags.isUseGenerationFence())) {
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
        boolean completed = false;
        try {
            transitionToJudging(submissionId);

            Optional<JudgeExecutionResult> result = runPipeline(job);
            if (result.isEmpty()) {
                markSystemError(submissionId);
                completed = true;
                return;
            }
            applyLegacyVerdict(submissionId, result.get());
            completed = true;
        } catch (Exception e) {
            log.error("Failed to process judge job for submission {}", submissionId, e);
            try {
                markSystemError(submissionId);
                completed = true;
            } catch (Exception terminalWriteFailure) {
                e.addSuppressed(terminalWriteFailure);
                log.error("Unable to persist System Error for submission {}", submissionId,
                        terminalWriteFailure);
            }
        } finally {
            releaseIfLeased(port, handle, completed, submissionId);
        }
    }

    private void transitionToJudging(String submissionId) {
        submissionWritePort.updateSubmissionResult(submissionId, SubmissionStatus.JUDGING, 0, 0.0, null);
    }

    private void applyLegacyVerdict(String submissionId, JudgeExecutionResult pipelineResult) {
        SubmissionStatus status = pipelineResult.status();
        String testDetailsJson = JudgeTestCaseDetailCodec.toJson(pipelineResult.testCaseDetails());
        submissionWritePort.updateSubmissionResult(submissionId, status,
                pipelineResult.maxRuntimeMs(), pipelineResult.maxMemoryMb(), testDetailsJson);
    }

    // -----------------------------------------------------------------------
    // Fenced path (lease CAS + heartbeat)
    // -----------------------------------------------------------------------

    private void processJobFenced(JudgeJob job, JudgeQueue port, JudgeJobHandle handle) {
        String submissionId = job.getSubmissionId();
        JudgeJobEnvelope envelope = handle != null ? handle.envelope() : null;
        boolean envelopeFence = envelope != null && envelope.isFenceAware();
        String attemptId = envelopeFence ? envelope.attemptId() : uuidGenerator.newId();

        Long observed = submissionFencePort.currentGeneration(submissionId);
        if (observed == null) {
            log.warn("Fenced judge: submission {} not found, abandoning", submissionId);
            releaseIfLeased(port, handle, true, submissionId);
            return;
        }
        long generation = envelopeFence ? envelope.generation() : observed;
        if (envelopeFence && observed.longValue() != generation) {
            log.info("Fenced judge: submission {} envelope gen {} is stale; current gen {}",
                    submissionId, generation, observed);
            releaseIfLeased(port, handle, true, submissionId);
            return;
        }

        boolean acquired = submissionFencePort.acquireLease(
                submissionId, attemptId, generation, LeaseConstants.LEASE_TTL_SECONDS);
        if (!acquired) {
            log.debug("Fenced judge: lease not acquired for submission {} gen {} (already moved)",
                    submissionId, generation);
            // The attempt is still owned elsewhere (the original worker
            // heartbeats its lease, or the DB-side reaper is recovering).
            // Do NOT ack: acking would retire the entry from the shared
            // consumer group's PEL while the first processing is still in
            // flight — a reaper reclaim of a slow job must not silently
            // drop the broker-level retry contract. nack leaves the entry
            // in the PEL: the owner's eventual XACK (same entry id) removes
            // it, and a superseded entry (generation bumped by DB recovery)
            // is acked by the stale-generation check above on the next
            // reclaim.
            releaseIfLeased(port, handle, false, submissionId);
            return;
        }

        ScheduledFuture<?> heartbeatTask = startHeartbeat(submissionId, attemptId);
        boolean completed = false;
        try {
            completed = executeAndWriteFenced(job, attemptId, generation);
        } finally {
            stopHeartbeat(heartbeatTask);
            releaseIfLeased(port, handle, completed, submissionId);
        }
    }

    private boolean executeAndWriteFenced(JudgeJob job, String attemptId, long generation) {
        String submissionId = job.getSubmissionId();
        try {
            Optional<JudgeExecutionResult> result = runPipeline(job);
            if (result.isEmpty()) {
                markSystemErrorFenced(submissionId, generation, attemptId);
                return true;
            }
            return writeVerdictFenced(submissionId, attemptId, generation, result.get());
        } catch (Exception e) {
            log.error("Failed to process fenced judge job for submission {}", submissionId, e);
            try {
                markSystemErrorFenced(submissionId, generation, attemptId);
                return true;
            } catch (Exception terminalWriteFailure) {
                e.addSuppressed(terminalWriteFailure);
                log.error("Unable to persist fenced System Error for submission {}",
                        submissionId, terminalWriteFailure);
                return false;
            }
        }
    }

    private boolean writeVerdictFenced(String submissionId, String attemptId, long generation,
                                       JudgeExecutionResult pipelineResult) {
        SubmissionStatus status = pipelineResult.status();
        String testDetailsJson = JudgeTestCaseDetailCodec.toJson(pipelineResult.testCaseDetails());
        try {
            boolean written = submissionWritePort.updateSubmissionResultFenced(
                    submissionId, status,
                    pipelineResult.maxRuntimeMs(), pipelineResult.maxMemoryMb(), testDetailsJson,
                    generation, attemptId);

            if (!written) {
                log.info("Fenced judge: verdict {} for submission {} gen {} dropped (superseded)",
                        status.wireValue(), submissionId, generation);
            }
            // false means stale/superseded in the fenced write contract, not a
            // transient failure. The result is therefore safe to acknowledge.
            return true;
        } catch (Exception e) {
            log.error("Unable to persist fenced verdict for submission {}",
                    submissionId, e);
            return false;
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

    private void releaseIfLeased(JudgeQueue port, JudgeJobHandle handle,
                                 boolean completed, String submissionId) {
        if (port != null && handle != null) {
            try {
                if (completed) {
                    port.ack(handle);
                } else {
                    port.nack(handle, "judge result was not durably written for " + submissionId);
                }
            } catch (Exception e) {
                log.warn("Failed to {} job handle {} on {}: {}",
                        completed ? "ack" : "nack", handle,
                        port.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    private Optional<JudgeExecutionResult> runPipeline(JudgeJob job) throws Exception {
        Long problemId = null;
        if (job.getProblemId() != null && !job.getProblemId().isBlank()) {
            try {
                problemId = Long.parseLong(job.getProblemId());
            } catch (NumberFormatException e) {
                log.warn("Invalid problemId {} for submission {}", job.getProblemId(), job.getSubmissionId());
            }
        }
        if (problemId == null || problemId <= 0) {
            log.warn("Cannot run judge pipeline: missing or non-positive problemId for submission {}", job.getSubmissionId());
            return Optional.empty();
        }
        return Optional.ofNullable(executionPipeline.execute(
                job.getLanguage(), job.getCode(),
                problemId, job.getUserId(), job.getSubmissionId()));
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
