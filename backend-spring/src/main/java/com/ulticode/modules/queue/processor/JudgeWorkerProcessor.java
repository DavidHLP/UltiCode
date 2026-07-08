package com.ulticode.modules.queue.processor;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.submission.config.FeatureFlagsProperties;
import com.ulticode.modules.submission.config.JudgeSourceProperties;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.queue.pipeline.JudgeExecutionPipeline;
import com.ulticode.modules.queue.pipeline.JudgeExecutionResult;
import com.ulticode.modules.queue.config.QueueConfig;
import com.ulticode.modules.queue.constants.QueueConstants;
import com.ulticode.modules.queue.dto.JobStatusDTO;
import com.ulticode.modules.queue.job.JudgeJob;
import com.ulticode.modules.queue.job.JobProcessor;
import com.ulticode.modules.queue.port.JudgeJobEnvelope;
import com.ulticode.modules.queue.port.JudgeJobHandle;
import com.ulticode.modules.queue.port.JudgeQueue;
import com.ulticode.modules.queue.service.QueueService;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.enums.CaseScope;
import com.ulticode.modules.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.fence.LeaseConstants;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.service.SubmissionService;
import com.ulticode.modules.websocket.contest.dto.SubmissionResultPayload;
import com.ulticode.modules.queue.port.SubmissionResultPushPort;
import com.ulticode.modules.contest.entity.ContestSubmission;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final SubmissionService submissionService;
    private final SubmissionResultPushPort submissionResultPushPort;
    private final ContestSubmissionMapper contestSubmissionMapper;
    private final JudgeExecutionPipeline executionPipeline;
    private final QueueConfig queueConfig;
    /**
     * ADR-003 M3b: mapper for the lease CAS (acquire/renew/fenced verdict).
     * Nullable so existing unit tests that mock SubmissionService still work.
     */
    private final SubmissionMapper submissionMapper;
    private final FeatureFlagsProperties featureFlags;
    /** Nullable; {@code judge.lease.miss_renew} is a no-op without a registry. */
    private final MeterRegistry meterRegistry;
    /**
     * ADR-003 M3c-3a: provider (not direct injection) so the worker compiles
     * even when no {@link JudgeQueue} bean is registered (i.e. before the
     * M3c-2 cutover). Resolves to null in M3a/M3b; resolves to the Streams
     * adapter once the port flag is on.
     */
    private final ObjectProvider<JudgeQueue> judgeQueueProvider;
    private final UuidGenerator uuidGenerator;

    private final AtomicInteger activeJobs = new AtomicInteger(0);

    /**
     * Single-thread heartbeat scheduler, created via {@link ScheduledThreadPoolExecutor}
     * (not {@link java.util.concurrent.Executors}, per the backend concurrency
     * rule). Lazily initialized because the fenced path is flag-gated and most
     * deployments run flag-off. One worker holds at most one lease at a time, so
     * a single-thread scheduler is sufficient and serializes heartbeats safely.
     */
    private volatile ScheduledExecutorService heartbeatExecutor;

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
        JudgeJobEnvelope envelope = handle.envelope();
        String submissionId = envelope.submissionId();
        String problemId = envelope.problemId();
        String userId = envelope.userId();
        String attemptId = envelope.attemptId() != null
                ? envelope.attemptId()
                : uuidGenerator.newId();
        long generation = envelope.generation() != null ? envelope.generation() : 1L;

        // 1. Acquire the lease using the dispatcher's attemptId so the
        //    fence CAS matches the outbox row's intent. affected = 0 ->
        //    already judging or generation moved; abandon + nack so the
        //    reaper's visibility timer can reclaim.
        int acquired = submissionMapper.acquireLease(
                submissionId, attemptId, generation, LeaseConstants.LEASE_TTL_SECONDS);
        if (acquired != 1) {
            log.debug("Port fenced judge: lease not acquired for submission {} gen {} (already moved)",
                    submissionId, generation);
            // nack with a reason so the broker retains the entry in the
            // PEL and the unacked reaper (M3c-2) can reclaim it after
            // visibilityTimeoutMs elapses. ack would lose the work
            // entirely; leaving it undelivered leaves the entry stuck.
            port.nack(handle, "lease-not-acquired:gen=" + generation);
            return;
        }

        ScheduledFuture<?> heartbeatTask = startHeartbeat(submissionId, attemptId);
        try {
            executeAndWriteFenced(
                    submissionId, problemId, userId,
                    envelope.language(), envelope.code(),
                    attemptId, generation);
        } finally {
            stopHeartbeat(heartbeatTask);
        }

        // Ack on success. Acquire-failure path above already returned
        // without ack; the reaper will reclaim those entries.
        port.ack(handle);
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
        if (featureFlags.isUseGenerationFence() && submissionMapper != null) {
            processJobFenced(job);
            return;
        }
        processJobLegacy(job);
    }

    /**
     * Legacy judging path (pre-ADR-003). Mark Judging, execute, write verdict
     * via {@code updateSubmissionResult}. Preserved verbatim so flag-off
     * deployments observe no behavior change.
     */
    private void processJobLegacy(JudgeJob job) {
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
                return;
            }

            submissionService.updateSubmissionResult(submissionId, result.verdict(),
                    result.maxRuntimeMs(), result.maxMemoryMb(), result.testCaseDetails());

            long memoryBytes = (long) (result.maxMemoryMb() * 1024 * 1024);
            String contestId = findContestIdBySubmissionId(submissionId);
            pushResult(userId, submissionId, problemId, result.verdict(),
                    result.maxRuntimeMs(), memoryBytes, contestId);
        } catch (Exception e) {
            log.error("Failed to process judge job for submission {}", submissionId, e);
            submissionService.updateSubmissionResult(submissionId, "System Error", 0, 0.0, null);
            String failedContestId = findContestIdBySubmissionId(submissionId);
            pushResult(userId, submissionId, problemId, "System Error", 0, 0L, failedContestId);
        }
    }



    /**
     * ADR-003 M3b fenced judging path. Acquires a lease via CAS, runs a
     * heartbeat thread, and writes the verdict through the fenced CAS.
     */
    private void processJobFenced(JudgeJob job) {
        String submissionId = job.getSubmissionId();
        String problemId = job.getProblemId();
        String userId = job.getUserId();
        String attemptId = uuidGenerator.newId();

        Submission current = submissionMapper.selectById(submissionId);
        if (current == null) {
            log.warn("Fenced judge: submission {} not found, abandoning", submissionId);
            return;
        }
        long generation = current.getGeneration() != null ? current.getGeneration() : 1L;

        int acquired = submissionMapper.acquireLease(
                submissionId, attemptId, generation, LeaseConstants.LEASE_TTL_SECONDS);
        if (acquired != 1) {
            log.debug("Fenced judge: lease not acquired for submission {} gen {} (already moved)",
                    submissionId, generation);
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
        }
    }

    /**
     * Execute the judging and write the verdict through the fenced CAS.
     * Delegates execution to {@link JudgeExecutionPipeline}.
     */
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

    // -----------------------------------------------------------------------
    // JobProcessor interface
    // -----------------------------------------------------------------------

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
            log.error("All retries exhausted for judge job {}, marking as System Error", job.getId(), error);
            submissionService.updateSubmissionResult(
                    job.getSubmissionId(), "System Error", 0, 0.0, null);
            String failedContestId = findContestIdBySubmissionId(job.getSubmissionId());
            pushResult(job.getUserId(), job.getSubmissionId(), job.getProblemId(),
                    "System Error", 0, 0L, failedContestId);
        }
    }

    private void pushResult(String userId, String submissionId, String problemId,
                            String status, int timeUsed, long memoryUsed, String contestId) {
        SubmissionResultPayload payload = SubmissionResultPayload.of(
                submissionId, contestId, problemId, userId, status, 0, timeUsed, memoryUsed);
        submissionResultPushPort.emitSubmissionResult(userId, payload);
    }

    private String findContestIdBySubmissionId(String submissionId) {
        // Non-critical path: a missing or unloadable contest mapping is
        // not a verdict-changing failure. We classify the failure modes so
        // genuine data-integrity issues surface as ERROR while transient
        // infra problems stay at WARN.
        try {
            ContestSubmission cs = contestSubmissionMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ContestSubmission>()
                            .eq(ContestSubmission::getSubmissionId, submissionId));
            return cs != null ? cs.getContestId() : null;
        } catch (org.springframework.dao.DataAccessException dae) {
            // Transient DB issues (connection, timeout) — keep judging live.
            log.warn("Transient DB error resolving contest id for submission {}; continuing without contest context",
                    submissionId, dae);
            return null;
        } catch (Exception e) {
            // Anything else (schema drift, unexpected TooManyResults, NPE
            // inside the mapper proxy) likely indicates a real bug; record
            // as ERROR for alerting but do not let it fail the judge.
            log.error("Unexpected error resolving contest id for submission {}; continuing without contest context",
                    submissionId, e);
            return null;
        }
    }
}
