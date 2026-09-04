package com.ulticode.modules.queue.processor;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.lifecycle.DrainGate;
import com.ulticode.modules.submission.port.JudgeFeatureFlagsPort;
import com.ulticode.modules.queue.config.QueueConfig;
import com.ulticode.modules.queue.constants.QueueConstants;
import com.ulticode.modules.queue.dto.JobStatusDTO;
import com.ulticode.modules.queue.job.JudgeJob;
import com.ulticode.modules.queue.job.JobProcessor;
import com.ulticode.submission.api.queue.JudgeJobEnvelope;
import com.ulticode.submission.api.queue.JudgeJobHandle;
import com.ulticode.submission.api.queue.JudgeQueue;
import com.ulticode.modules.queue.service.QueueService;
import lombok.RequiredArgsConstructor;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Judge worker that polls the Redis judge queue and processes submissions.
 *
 * <p>Wires together QueueService, CodeExecutionService, and SubmissionVerdictWritePort
 * to form the judging pipeline. Post-verdict effects are consumed from the
 * durable SubmissionJudged event after the verdict transaction commits:
 *
 * <ol>
 *   <li>Poll job from Redis queue
 *   <li>Set submission status to "Judging"
 *   <li>Load test cases, build the runtime-private JudgeRunRequest, execute via Docker sandbox
 *   <li>Determine verdict via {@link VerdictResolver#reduceWire} aggregating each case's wire value
 *       into a single {@code SubmissionStatus} (ADR-001; severity priority encoded in
 *       {@code SubmissionStatus#getSeverity()}, replacing the old stringly-typed priority comparison)
 *   <li>Write result to Submission entity and its durable result event
 * </ol>
 */
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "queue.judge.enabled",
        havingValue = "true",
        matchIfMissing = true
)
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
        "'${app.runtime.role:api}' == 'judge'")
public class JudgeWorkerProcessor implements JobProcessor<JudgeJob> {

    private final QueueService queueService;
    private final QueueConfig queueConfig;
    private final JudgeFeatureFlagsPort featureFlags;
    private final ObjectProvider<JudgeQueue> judgeQueueProvider;
    private final JudgeAttemptExecutor attemptExecutor;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    private final AtomicInteger activeJobs = new AtomicInteger(0);
    private final DrainGate drainGate = new DrainGate();
    /** Epoch millis of the last successfully processed job; 0 = never. */
    private final java.util.concurrent.atomic.AtomicLong lastSuccessMillis = new java.util.concurrent.atomic.AtomicLong(0);


    @jakarta.annotation.PostConstruct
    void registerSloGauges() {
        // Review 2026-08-25 FINAL P1: worker consumption/queue SLO gauges.
        // Resolved lazily so gauge reads tolerate beans not yet available and
        // Redis hiccups (reported as NaN / -1 instead of breaking scrapes).
        meterRegistry.gauge("judge.worker.last.success.age.seconds", lastSuccessMillis,
                ms -> ms.get() == 0 ? -1d
                        : (System.currentTimeMillis() - ms.get()) / 1000d);
        meterRegistry.gauge("judge.queue.pending.depth", judgeQueueProvider, provider -> {
            JudgeQueue queue = provider.getIfAvailable();
            if (queue == null) {
                return Double.NaN;
            }
            try {
                return (double) queue.pendingDepth();
            } catch (RuntimeException e) {
                return Double.NaN;
            }
        });
    }

    @Override
    public String getJobType() {
        return QueueConstants.JUDGE_QUEUE;
    }

    /**
     * Poll the judge queue and process the next job.
     * Guarded by maxConcurrentJobs to prevent unbounded concurrency.
     */
    @Scheduled(
            fixedDelayString = "${queue.poll-interval-ms:1000}",
            initialDelayString = "${queue.judge.initial-delay-ms:5000}"
    )
    public void pollAndProcess() {
        if (!drainGate.tryEnter()) {
            return;
        }
        try {
            if (featureFlags.isJudgeQueueUsePort()) {
                return;
            }
            if (!tryAcquireJobSlot()) {
                return;
            }

            try {
                Object polled = queueService.pollJob(QueueConstants.JUDGE_QUEUE);
                if (!(polled instanceof JudgeJob judgeJob)) {
                    return;
                }
                processJob(judgeJob);
            } finally {
                releaseJobSlot();
            }
        } catch (Exception e) {
            log.error("JudgeWorkerProcessor.pollAndProcess failed", e);
        } finally {
            drainGate.leave();
        }
    }

    /**
     * ADR-003 M3c-3a: poll the {@link JudgeQueue} port for v1/v2 envelopes
     * and process them through the fenced path. Runs in parallel to
     * {@link #pollAndProcess()}; whichever port is active drives
     * production. The two loops are mutually exclusive at the broker
     * (ADR-005 F8): when {@code app.features.judge-queue.use-port=true}
     * the dispatcher stops writing to the legacy RQueue, so this loop
     * is the only consumer.
     *
     * <p>No-op when the port flag is off or the port bean is not
     * registered (i.e. before the M3c-2 cutover); the legacy loop above
     * keeps running unchanged.
     */
    @Scheduled(
            fixedDelayString = "${judge.port.poll-interval-ms:1000}",
            initialDelayString = "${judge.port.initial-delay-ms:5000}"
    )
    public void pollAndProcessFromPort() {
        if (!drainGate.tryEnter()) {
            return;
        }
        try {
            if (!featureFlags.isJudgeQueueUsePort()) {
                return;
            }
            JudgeQueue port = judgeQueueProvider.getIfAvailable();
            if (port == null) {
                return;
            }
            if (!hasJobCapacity()) {
                return;
            }
            // Short poll so the loop can drain a few entries per tick.
            java.util.Optional<JudgeJobHandle> maybeHandle = port.poll(500L);
            if (maybeHandle.isEmpty()) {
                return;
            }
            JudgeJobHandle handle = maybeHandle.get();
            if (drainGate.isDraining()) {
                port.nack(handle, "judge worker is draining");
                return;
            }
            if (!tryAcquireJobSlot()) {
                port.nack(handle, "judge worker concurrency limit reached");
                return;
            }
            try {
                processJobFromPort(port, handle);
            } finally {
                releaseJobSlot();
            }
        } catch (Exception e) {
            log.error("JudgeWorkerProcessor.pollAndProcessFromPort failed", e);
        } finally {
            drainGate.leave();
        }
    }

    /**
     * ADR-003 M3c-3b: process a reclaimed handle routed from the
     * unacked Streams reaper (codex P1 #3 fix). The handle is a normal
     * {@link JudgeJobHandle} returned by {@code claimIdle}; this method
     * is a public entry point so the reaper (in
     * {@code queue.outbox.reaper}) can drive the same fenced execution
     * path the worker uses for neverDelivered entries. Synchronous so
     * the reaper's claim-then-ack window stays small.
     */
    public void processReclaimedHandle(JudgeQueue port, JudgeJobHandle handle) {
        if (!drainGate.tryEnter()) {
            port.nack(handle, "judge worker is draining");
            return;
        }
        if (!tryAcquireJobSlot()) {
            try {
                port.nack(handle, "judge worker concurrency limit reached");
            } finally {
                drainGate.leave();
            }
            return;
        }
        try {
            processJobFromPort(port, handle);
        } finally {
            releaseJobSlot();
            drainGate.leave();
        }
    }

    @EventListener
    public void onContextClosed(ContextClosedEvent ignored) {
        drainGate.beginDrain();
    }

    /** Used by the Streams reaper to avoid claiming work while this worker is full. */
    public boolean hasCapacity() {
        return hasJobCapacity();
    }

    private boolean hasJobCapacity() {
        return queueConfig.getMaxConcurrentJobs() > 0
                && activeJobs.get() < queueConfig.getMaxConcurrentJobs();
    }

    private boolean tryAcquireJobSlot() {
        int max = queueConfig.getMaxConcurrentJobs();
        if (max <= 0) {
            return false;
        }
        while (true) {
            int current = activeJobs.get();
            if (current >= max) {
                return false;
            }
            if (activeJobs.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    private void releaseJobSlot() {
        activeJobs.decrementAndGet();
    }

    /**
     * ADR-003 M3c-3a fenced judging path for envelopes read from the
     * {@link JudgeQueue} port. The v2 envelope carries its own
     * {@code attemptId} and {@code generation} (set by the dispatcher on
     * commit) so the worker does not generate either: it uses the
     * dispatcher's claim token, ensuring the fence CAS targets the same
     * (generation, attemptId) pair the dispatcher recorded in the
     * outbox row.
     */
    private void processJobFromPort(JudgeQueue port, JudgeJobHandle handle) {
        // Reconstruct a JudgeJob from the leased envelope and hand it to
        // the executor. The executor owns claim / heartbeat / verdict /
        // release; this adapter only translates the queue-specific envelope
        // shape.
        JudgeJobEnvelope envelope = handle.envelope();
        JudgeJob job = new JudgeJob();
        job.setId(envelope.id());
        job.setSubmissionId(envelope.submissionId());
        job.setProblemId(envelope.problemId());
        job.setUserId(envelope.userId());
        job.setLanguage(envelope.language());
        job.setCode(envelope.code());
        // The envelope is the only per-dispatch source for the limits (the
        // outbox payload carries them); without this the rebuilt job keeps
        // JudgeJob's hard-coded defaults and the v2 contract fields are
        // silently discarded at the consumer boundary.
        job.setTimeLimitMs(envelope.timeLimitMs());
        job.setMemoryLimitKb(envelope.memoryLimitKb());
        attemptExecutor.runAttempt(job, port, handle);
        lastSuccessMillis.set(System.currentTimeMillis());
    }

    /**
     * Process a judge job: execute code, determine verdict, write result, and
     * persist the durable post-verdict event.
     *
     * <p>Branches on {@code app.features.use-generation-fence}:
     * <ul>
     *   <li>flag-off -> legacy path: selectById + updateSubmissionResult (no lease).</li>
     *   <li>flag-on -> fenced path: acquireLease CAS, heartbeat while judging,
     *       writeVerdictFenced so stale results from a superseded generation are
     *       dropped (ADR-003 M3b).</li>
     * </ul>
     */
    public void processJob(JudgeJob job) {
        // Public entry used by QueueService for the RQueue path. The
        // executor decides legacy vs fenced based on its own FeatureFlags
        // (consistent across all callers).
        attemptExecutor.runAttempt(job, null, null);
        lastSuccessMillis.set(System.currentTimeMillis());
    }

    @Override
    public JobStatusDTO process(JudgeJob job) throws Exception {
        processJob(job);
        return JobStatusDTO.builder()
                .jobId(job.getId())
                .jobType(getJobType())
                .status(QueueConstants.JobStatus.COMPLETED)
                .build();
    }

    @Override
    public boolean shouldRetry(JudgeJob job, Exception error, int attempts, int maxRetries) {
        if (error.getMessage() != null
                && error.getMessage().toLowerCase().contains("compile")) {
            return false;
        }
        if (error instanceof BusinessException bizEx
                && bizEx.getErrorCode().code() == 40005) {
            return false;
        }
        return attempts < maxRetries;
    }

    @Override
    public void onFailure(JudgeJob job, Exception error) {
        if (shouldRetry(job, error, job.getAttempts(), job.getMaxRetries())) {
            try {
                long delay = (long) (2000 * Math.pow(2, job.getAttempts()));
                Thread.sleep(delay);
                queueService.retryJob(job.getId());
                log.info("Retrying judge job {} after {}ms", job.getId(), delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Retry sleep interrupted for job {}", job.getId());
            }
        } else {
            // Hand the final-failure side-effect to the executor so the
            // same System-Error write + push lives in one place.
            log.error("All retries exhausted for judge job {}, marking as System Error", job.getId(), error);
            attemptExecutor.markExhausted(job);
        }
    }
}
