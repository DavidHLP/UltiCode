package com.ulticode.modules.queue.processor;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.submission.config.FeatureFlagsProperties;
import com.ulticode.modules.queue.config.QueueConfig;
import com.ulticode.modules.queue.constants.QueueConstants;
import com.ulticode.modules.queue.dto.JobStatusDTO;
import com.ulticode.modules.queue.job.JudgeJob;
import com.ulticode.modules.queue.job.JobProcessor;
import com.ulticode.modules.queue.port.JudgeJobEnvelope;
import com.ulticode.modules.queue.port.JudgeJobHandle;
import com.ulticode.modules.queue.port.JudgeQueue;
import com.ulticode.modules.queue.service.QueueService;
import lombok.RequiredArgsConstructor;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Judge worker that polls the Redis judge queue and processes submissions.
 *
 * <p>Wires together QueueService, CodeExecutionService, SubmissionService,
 * and SubmissionResultPushPort to form the complete judging pipeline:
 *
 * <ol>
 *   <li>Poll job from Redis queue
 *   <li>Set submission status to "Judging"
 *   <li>Load test cases, build RunSubmissionDTO, execute via Docker sandbox
 *   <li>Determine verdict via {@link VerdictResolver#reduceWire} aggregating each case's wire value
 *       into a single {@code SubmissionStatus} (ADR-001; severity priority encoded in
 *       {@code SubmissionStatus#getSeverity()}, replacing the old stringly-typed priority comparison)
 *   <li>Write result to Submission entity
 *   <li>Push WebSocket notification to user
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "queue.judge.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class JudgeWorkerProcessor implements JobProcessor<JudgeJob> {

    private final QueueService queueService;
    private final QueueConfig queueConfig;
    private final FeatureFlagsProperties featureFlags;
    private final ObjectProvider<JudgeQueue> judgeQueueProvider;
    private final JudgeAttemptExecutor attemptExecutor;

    private final AtomicInteger activeJobs = new AtomicInteger(0);


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
        try {
            if (activeJobs.get() >= queueConfig.getMaxConcurrentJobs()) {
                return;
            }

            Object polled = queueService.pollJob(QueueConstants.JUDGE_QUEUE);
            if (!(polled instanceof JudgeJob judgeJob)) {
                return;
            }

            activeJobs.incrementAndGet();
            try {
                processJob(judgeJob);
            } finally {
                activeJobs.decrementAndGet();
            }
        } catch (Exception e) {
            log.error("JudgeWorkerProcessor.pollAndProcess failed", e);
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
        if (!featureFlags.getJudgeQueue().isUsePort()) {
            return;
        }
        JudgeQueue port = judgeQueueProvider.getIfAvailable();
        if (port == null) {
            return;
        }
        try {
            if (activeJobs.get() >= queueConfig.getMaxConcurrentJobs()) {
                return;
            }
            // Short poll so the loop can drain a few entries per tick.
            java.util.Optional<JudgeJobHandle> maybeHandle = port.poll(500L);
            if (maybeHandle.isEmpty()) {
                return;
            }
            JudgeJobHandle handle = maybeHandle.get();
            activeJobs.incrementAndGet();
            try {
                processJobFromPort(port, handle);
            } finally {
                activeJobs.decrementAndGet();
            }
        } catch (Exception e) {
            log.error("JudgeWorkerProcessor.pollAndProcessFromPort failed", e);
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
        processJobFromPort(port, handle);
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
        // push / release; this adapter only translates the queue-specific
        // envelope shape.
        JudgeJobEnvelope envelope = handle.envelope();
        JudgeJob job = new JudgeJob();
        job.setId(envelope.id());
        job.setSubmissionId(envelope.submissionId());
        job.setProblemId(envelope.problemId());
        job.setUserId(envelope.userId());
        job.setLanguage(envelope.language());
        job.setCode(envelope.code());
        attemptExecutor.runAttempt(job, port, handle);
    }

    /**
     * Process a judge job: execute code, determine verdict, write result, push WebSocket.
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
                && bizEx.getErrorCode() == ErrorCode.SUBMISSION_LANGUAGE_UNSUPPORTED) {
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
