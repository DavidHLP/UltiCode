package com.ulticode.modules.queue.processor;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.config.FeatureFlagsProperties;
import com.ulticode.common.config.JudgeSourceProperties;
import com.ulticode.modules.problem.entity.TestCase;
import com.ulticode.modules.problem.mapper.TestCaseMapper;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.ulticode.modules.problem.entity.ProblemExample;
import com.ulticode.modules.problem.mapper.ProblemExampleMapper;
import com.ulticode.modules.queue.config.QueueConfig;
import com.ulticode.modules.queue.constants.QueueConstants;
import com.ulticode.modules.queue.dto.JobStatusDTO;
import com.ulticode.modules.queue.job.JudgeJob;
import com.ulticode.modules.queue.job.JobProcessor;
import com.ulticode.modules.queue.port.JudgeJobEnvelope;
import com.ulticode.modules.queue.port.JudgeJobHandle;
import com.ulticode.modules.queue.port.JudgeQueue;
import com.ulticode.modules.queue.port.VerdictMetricsParser;
import com.ulticode.modules.queue.service.QueueService;
import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.enums.CaseScope;
import com.ulticode.modules.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.fence.LeaseConstants;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.service.CodeExecutionService;
import com.ulticode.modules.submission.service.SubmissionService;
import com.ulticode.modules.submission.service.VerdictResolver;
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
import java.util.UUID;
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
    private final CodeExecutionService codeExecutionService;
    private final SubmissionService submissionService;
    private final SubmissionResultPushPort submissionResultPushPort;
    private final ContestSubmissionMapper contestSubmissionMapper;
    private final ProblemExampleMapper problemExampleMapper;
    private final TestCaseMapper testCaseMapper;
    private final JudgeSourceProperties judgeSourceProperties;
    private final QueueConfig queueConfig;
    private final ObjectMapper objectMapper;
    private final VerdictResolver verdictResolver;
    /**
     * Wire-string → typed-primitive parser for sandbox runtime / memory fields.
     * Extracted out of this class because the parser has nothing to do with
     * sandbox dispatch — the wire format is decided by the sandbox adapter,
     * and the worker should not carry the parser logic in its hot path.
     * Tests for the parser do not need the worker's 18 collaborators.
     */
    private final VerdictMetricsParser verdictMetricsParser;
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
                : UUID.randomUUID().toString();
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
            // Mark as judging
            submissionService.updateSubmissionResult(submissionId, "Judging", 0, null, null);

            // P0-1: branch on judge source flag (test_cases vs problem_examples).
            // Both paths fail closed (System Error) when no eligible cases exist;
            // the only difference is which mapper supplies the cases and whether
            // we write caseId/caseScope into TestCaseDetail.
            if (judgeSourceProperties.isUseTestCases()) {
                processJobWithTestCases(job, submissionId, problemId, userId);
            } else {
                processJobWithProblemExamples(job, submissionId, problemId, userId);
            }
        } catch (Exception e) {
            log.error("Failed to process judge job for submission {}", submissionId, e);
            submissionService.updateSubmissionResult(submissionId, "System Error", 0, 0.0, null);
            String failedContestId = findContestIdBySubmissionId(submissionId);
            pushResult(userId, submissionId, problemId, "System Error", 0, 0L, failedContestId);
        }
    }

    /**
     * P0-1 primary path: source cases from the canonical {@code test_cases} table.
     * <p>
     * Filters out draft ({@code isSample=false, isHidden=false}) and illegal
     * ({@code isSample=true, isHidden=true}) rows; soft-deleted rows are
     * excluded by {@code @TableLogic}. An empty result fails closed with
     * System Error — never falls back to {@code problem_examples}.
     * <p>
     * Writes {@code caseId} and {@code caseScope} into each {@code TestCaseDetail}
     * using {@code cr.getTestCaseId()} (passed through from the D-form harness)
     * to look up the canonical case in a pre-built map. Unknown IDs log a
     * system error and are recorded as scope=null (treated as legacy sample at
     * the user-facing projection layer, never as HIDDEN).
     */
    private void processJobWithTestCases(JudgeJob job, String submissionId, String problemId, String userId) {
        List<TestCase> cases = testCaseMapper.findActiveCasesForJudging(Long.parseLong(problemId));
        if (cases == null || cases.isEmpty()) {
            log.warn("No eligible test_cases for problem {} (fail closed: System Error, no problem_examples fallback)",
                    problemId);
            submissionService.updateSubmissionResult(submissionId, "System Error", 0, 0.0, null);
            pushResult(userId, submissionId, problemId, "System Error", 0, 0L, null);
            return;
        }

        Map<String, TestCase> byId = cases.stream().collect(
                Collectors.toMap(TestCase::getId, Function.identity(), (a, b) -> a));

        RunSubmissionDTO runDto = buildRunSubmissionDTOFromTestCases(job.getLanguage(), job.getCode(), cases);
        RunResultDTO result = codeExecutionService.execute(runDto, Long.parseLong(problemId), userId);

        String verdict = determineVerdict(result.getCases());

        long maxRuntimeMs = 0;
        double maxMemoryMb = 0.0;
        for (RunResultDTO.RunCaseResult caseResult : result.getCases()) {
            maxRuntimeMs = Math.max(maxRuntimeMs, parseRuntimeMs(caseResult.getRuntime()));
            maxMemoryMb = Math.max(maxMemoryMb, parseMemoryMb(caseResult.getMemory()));
        }

        List<Submission.TestCaseDetail> testCaseDetails = buildTestCaseDetailsWithScope(
                result.getCases(), byId, submissionId, problemId);

        submissionService.updateSubmissionResult(submissionId, verdict, (int) maxRuntimeMs, maxMemoryMb, testCaseDetails);

        long memoryBytes = (long) (maxMemoryMb * 1024 * 1024);
        String contestId = findContestIdBySubmissionId(submissionId);
        pushResult(userId, submissionId, problemId, verdict, (int) maxRuntimeMs, memoryBytes, contestId);
    }

    /**
     * Legacy problem_examples path. Preserved verbatim for short-term rollback
     * during P0-1 cutover; slated for deletion in Phase 3 cleanup (task #7).
     * <p>
     * No {@code caseId} or {@code caseScope} is written — those fields stay
     * {@code null} on the resulting {@code TestCaseDetail}, and the user-facing
     * projection layer treats null as legacy sample.
     */
    private void processJobWithProblemExamples(JudgeJob job, String submissionId, String problemId, String userId) {
        List<ProblemExample> examples = problemExampleMapper.findByProblemIdOrderByOrder(Long.parseLong(problemId));
        if (examples == null || examples.isEmpty()) {
            log.warn("No problem examples found for problem {}", problemId);
            submissionService.updateSubmissionResult(submissionId, "System Error", 0, 0.0, null);
            pushResult(userId, submissionId, problemId, "System Error", 0, 0L, null);
            return;
        }

        RunSubmissionDTO runDto = buildRunSubmissionDTO(job, examples);
        RunResultDTO result = codeExecutionService.execute(runDto, Long.parseLong(problemId), userId);

        String verdict = determineVerdict(result.getCases());

        long maxRuntimeMs = 0;
        double maxMemoryMb = 0.0;
        for (RunResultDTO.RunCaseResult caseResult : result.getCases()) {
            maxRuntimeMs = Math.max(maxRuntimeMs, parseRuntimeMs(caseResult.getRuntime()));
            maxMemoryMb = Math.max(maxMemoryMb, parseMemoryMb(caseResult.getMemory()));
        }

        List<Submission.TestCaseDetail> testCaseDetails = result.getCases().stream()
                .map(cr -> {
                    Submission.TestCaseDetail detail = new Submission.TestCaseDetail();
                    detail.setStatus(cr.getStatus());
                    detail.setTime((int) parseRuntimeMs(cr.getRuntime()));
                    detail.setMemory(parseMemoryMb(cr.getMemory()));
                    detail.setOutput(cr.getOutput());
                    detail.setExpectedOutput(cr.getExpectedOutput());
                    detail.setDetail(cr.getDetail());
                    return detail;
                })
                .toList();

        submissionService.updateSubmissionResult(submissionId, verdict, (int) maxRuntimeMs, maxMemoryMb, testCaseDetails);

        long memoryBytes = (long) (maxMemoryMb * 1024 * 1024);
        String contestId = findContestIdBySubmissionId(submissionId);
        pushResult(userId, submissionId, problemId, verdict, (int) maxRuntimeMs, memoryBytes, contestId);
    }

    /**
     * Build {@code TestCaseDetail} list with {@code caseId} and {@code caseScope}
     * populated from the canonical test_cases lookup. An unknown {@code testCaseId}
     * from the sandbox result logs a system error (no hidden input/output is
     * logged) and leaves {@code caseId}/{@code caseScope} null — treated as
     * legacy sample by the user-facing projection layer, never as HIDDEN.
     */
    private List<Submission.TestCaseDetail> buildTestCaseDetailsWithScope(
            List<RunResultDTO.RunCaseResult> caseResults,
            Map<String, TestCase> byId,
            String submissionId,
            String problemId) {
        List<Submission.TestCaseDetail> details = new ArrayList<>(caseResults.size());
        for (RunResultDTO.RunCaseResult cr : caseResults) {
            Submission.TestCaseDetail detail = new Submission.TestCaseDetail();
            detail.setStatus(cr.getStatus());
            detail.setTime((int) parseRuntimeMs(cr.getRuntime()));
            detail.setMemory(parseMemoryMb(cr.getMemory()));
            detail.setOutput(cr.getOutput());
            detail.setExpectedOutput(cr.getExpectedOutput());
            detail.setDetail(cr.getDetail());

            String tcId = cr.getTestCaseId();
            if (tcId != null && !tcId.isBlank()) {
                TestCase tc = byId.get(tcId);
                if (tc != null) {
                    detail.setCaseId(tc.getId());
                    if (Boolean.TRUE.equals(tc.getIsHidden())) {
                        detail.setCaseScope(CaseScope.HIDDEN);
                    } else if (Boolean.TRUE.equals(tc.getIsSample())) {
                        detail.setCaseScope(CaseScope.SAMPLE);
                    }
                    // isSample=false, isHidden=false (draft) -> caseScope stays null
                } else {
                    log.error("sandbox returned testCaseId={} not in test_cases for submission={} problem={} (recording system error, no hidden I/O logged)",
                            tcId, submissionId, problemId);
                }
            }
            details.add(detail);
        }
        return details;
    }

    /**
     * ADR-003 M3b fenced judging path. Acquires a lease via CAS, runs a
     * heartbeat thread that renews the lease while judging, and writes the
     * verdict through {@code writeVerdictFenced} so a stale worker whose
     * generation was bumped cannot overwrite the newer result.
     *
     * <p>Lifecycle:
     * <ol>
     *   <li>selectById to read the current generation.
     *   <li>acquireLease CAS (Pending -> Judging, attempt = UUID). affected = 0
     *       means another worker or a rejudge already moved the row -> abandon.</li>
     *   <li>Start heartbeat; each tick renewLease. On affected = 0 the attempt
     *       lost the lease (reaper bumped generation) -> abort judging.</li>
     *   <li>Execute, determine verdict.</li>
     *   <li>Stop heartbeat.</li>
     *   <li>writeVerdictFenced. affected = 0 -> stale result dropped + metric.</li>
     * </ol>
     */
    private void processJobFenced(JudgeJob job) {
        String submissionId = job.getSubmissionId();
        String problemId = job.getProblemId();
        String userId = job.getUserId();
        String attemptId = UUID.randomUUID().toString();

        // 1. Read the current generation before attempting to acquire the lease.
        Submission current = submissionMapper.selectById(submissionId);
        if (current == null) {
            log.warn("Fenced judge: submission {} not found, abandoning", submissionId);
            return;
        }
        long generation = current.getGeneration() != null ? current.getGeneration() : 1L;

        // 2. Acquire the lease. affected = 0 -> already judging or generation
        //    moved; abandon this job (the reaper or another worker owns it).
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
     * Execute the judging and write the verdict through the fenced CAS. Shared
     * by the M3b fenced path (JudgeJob from legacy RQueue) and the M3c-3a
     * port path (JudgeJobEnvelope from {@link JudgeQueue}). Inputs are
     * primitive strings + the dispatcher's (or locally-generated)
     * {@code attemptId} + {@code generation} so neither caller has to
     * expose its envelope/job structure to the other.
     */
    private void executeAndWriteFenced(String submissionId, String problemId, String userId,
                                       String language, String code,
                                       String attemptId, long generation) {
        try {
            // Load examples as judge cases.
            List<ProblemExample> examples = problemExampleMapper.findByProblemIdOrderByOrder(Long.parseLong(problemId));
            if (examples == null || examples.isEmpty()) {
                log.warn("No problem examples found for problem {}", problemId);
                boolean written = submissionService.updateSubmissionResultFenced(
                        submissionId, generation, attemptId, "System Error", 0, 0.0, null);
                if (written) {
                    pushResult(userId, submissionId, problemId, "System Error", 0, 0L, null);
                }
                return;
            }

            RunSubmissionDTO runDto = buildRunSubmissionDTO(language, code, examples);
            RunResultDTO result = codeExecutionService.execute(runDto, Long.parseLong(problemId), userId);

            String verdict = determineVerdict(result.getCases());

            long maxRuntimeMs = 0;
            double maxMemoryMb = 0.0;
            for (RunResultDTO.RunCaseResult caseResult : result.getCases()) {
                maxRuntimeMs = Math.max(maxRuntimeMs, parseRuntimeMs(caseResult.getRuntime()));
                maxMemoryMb = Math.max(maxMemoryMb, parseMemoryMb(caseResult.getMemory()));
            }

            List<Submission.TestCaseDetail> testCaseDetails = result.getCases().stream()
                    .map(cr -> {
                        Submission.TestCaseDetail detail = new Submission.TestCaseDetail();
                        detail.setStatus(cr.getStatus());
                        detail.setTime((int) parseRuntimeMs(cr.getRuntime()));
                        detail.setMemory(parseMemoryMb(cr.getMemory()));
                        detail.setOutput(cr.getOutput());
                        detail.setExpectedOutput(cr.getExpectedOutput());
                        detail.setDetail(cr.getDetail());
                        return detail;
                    })
                    .toList();

            // Fenced verdict write. affected = 0 -> the generation was bumped
            // (rejudge / reaper) while we judged; the result is dropped.
            boolean written = submissionService.updateSubmissionResultFenced(
                    submissionId, generation, attemptId, verdict,
                    (int) maxRuntimeMs, maxMemoryMb, testCaseDetails);

            if (written) {
                long memoryBytes = (long) (maxMemoryMb * 1024 * 1024);
                String contestId = findContestIdBySubmissionId(submissionId);
                pushResult(userId, submissionId, problemId, verdict, (int) maxRuntimeMs, memoryBytes, contestId);
            } else {
                log.info("Fenced judge: verdict {} for submission {} gen {} dropped (superseded)",
                        verdict, submissionId, generation);
            }
        } catch (Exception e) {
            log.error("Failed to process fenced judge job for submission {}", submissionId, e);
            // Best-effort System Error write through the fence; if the fence
            // rejects it the row stays Judging until the reaper recovers it.
            boolean written = submissionService.updateSubmissionResultFenced(
                    submissionId, generation, attemptId, "System Error", 0, 0.0, null);
            if (written) {
                String failedContestId = findContestIdBySubmissionId(submissionId);
                pushResult(userId, submissionId, problemId, "System Error", 0, 0L, failedContestId);
            }
        }
    }

    /**
     * Start a heartbeat that renews the lease every {@code HEARTBEAT_INTERVAL_MS}.
     * If a renewal returns affected = 0 (the attempt lost the lease — reaper
     * bumped generation), the heartbeat logs and records the miss; the verdict
     * write will then be rejected by the fence anyway, so there is no separate
     * cancellation signal needed.
     *
     * <p>The scheduler is a {@link ScheduledThreadPoolExecutor} (one thread,
     * named for diagnostics), created directly rather than via
     * {@link java.util.concurrent.Executors} per the backend concurrency rule.
     */
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

    /**
     * Cancel this job's heartbeat task. The shared single-thread executor is
     * left running (lazy-init, reused across jobs); only this job's scheduled
     * future is cancelled ({@code mayInterruptIfRunning=false} — the renew
     * itself is a quick DB UPDATE and must not be interrupted mid-statement).
     */
    private void stopHeartbeat(ScheduledFuture<?> heartbeatTask) {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
    }

    /**
     * Lazily create the shared single-thread heartbeat scheduler. Double-checked
     * locked because the poll loop is single-threaded but the guard is cheap.
     */
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

    /**
     * Increment the {@code judge.lease.miss_renew} counter. No-op without a registry.
     */
    private void incrementLeaseMissRenew() {
        if (meterRegistry != null) {
            meterRegistry.counter("judge.lease.miss_renew").increment();
        }
    }

    /**
     * Minimal daemon thread factory that names threads for diagnostics (backend
     * concurrency rule: meaningful thread names for stack-trace triage).
     */
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
        // Don't retry compile errors
        if (error.getMessage() != null
                && error.getMessage().toLowerCase().contains("compile")) {
            return false;
        }
        // Don't retry unsupported language errors
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

    /**
     * Determine the final verdict from case results using {@link VerdictResolver}
     * (ADR-001). Severity priority embedded in {@link SubmissionStatus#getSeverity()}:
     * Sandbox Error / System Error > Compile Error > Runtime Error > Memory/Output
     * Limit Exceeded > Time Limit Exceeded > Wrong Answer > Presentation Error > Accepted.
     * Returns the wire-string form so callers that still hold String-typed status
     * fields keep working unchanged.
     */
    String determineVerdict(List<RunResultDTO.RunCaseResult> cases) {
        if (cases == null || cases.isEmpty()) {
            return SubmissionStatus.SYSTEM_ERROR.wireValue();
        }
        List<String> caseWireValues = new ArrayList<>(cases.size());
        for (RunResultDTO.RunCaseResult caseResult : cases) {
            caseWireValues.add(caseResult.getStatus());
        }
        return verdictResolver.reduceWire(caseWireValues).wireValue();
    }

    /**
     * Parse runtime string like "123ms" to milliseconds.
     * @deprecated delegate to {@link VerdictMetricsParser} — kept temporarily
     *             so existing tests that call {@code processor.parseRuntimeMs(...)}
     *             still resolve; new code should inject the parser instead.
     */
    @Deprecated
    long parseRuntimeMs(String runtime) {
        return verdictMetricsParser.parseRuntimeMs(runtime);
    }

    /**
     * Parse memory string like "4.2MB" to megabytes.
     * @deprecated delegate to {@link VerdictMetricsParser} — kept temporarily
     *             so existing tests that call {@code processor.parseMemoryMb(...)}
     *             still resolve; new code should inject the parser instead.
     */
    @Deprecated
    double parseMemoryMb(String memory) {
        return verdictMetricsParser.parseMemoryMb(memory);
    }

    private RunSubmissionDTO buildRunSubmissionDTO(JudgeJob job, List<ProblemExample> examples) {
        return buildRunSubmissionDTO(job.getLanguage(), job.getCode(), examples);
    }

    /**
     * Primitive-input overload shared by the M3b fenced path (JudgeJob)
     * and the M3c-3a port path (JudgeJobEnvelope). Extracted so neither
     * caller has to expose its envelope type to the other.
     */
    private RunSubmissionDTO buildRunSubmissionDTO(String language, String code, List<ProblemExample> examples) {
        RunSubmissionDTO runDto = new RunSubmissionDTO();
        runDto.setLanguage(language);
        runDto.setCode(code);
        runDto.setTestCases(examples.stream().map(tc -> {
            RunSubmissionDTO.RunTestCase rtc = new RunSubmissionDTO.RunTestCase();
            rtc.setId(String.valueOf(tc.getId()));
            rtc.setLabel("Case " + tc.getExampleOrder());
            rtc.setOutput(tc.getOutputText());

            List<RunSubmissionDTO.RunInput> runInputs = new ArrayList<>();
            // Parse structured inputs from JSON if available
            if (tc.getInputs() != null && !tc.getInputs().isBlank()) {
                try {
                    List<Map<String, Object>> inputs = objectMapper.readValue(
                            tc.getInputs(), new TypeReference<List<Map<String, Object>>>() {});
                    for (int i = 0; i < inputs.size(); i++) {
                        Map<String, Object> item = inputs.get(i);
                        RunSubmissionDTO.RunInput ri = new RunSubmissionDTO.RunInput();
                        ri.setId(String.valueOf(i));
                        Object nameObj = item.get("name");
                        Object labelObj = item.get("label");
                        String name = (nameObj != null ? nameObj : (labelObj != null ? labelObj : "input")).toString();
                        ri.setLabel(name);
                        ri.setName(name);
                        Object valueObj = item.get("value");
                        ri.setValue(valueObj != null ? valueObj.toString() : "");
                        // CR fix (Phase 5.5 #3): forward the OJ data-type hint
                        // from the stored problem input to RunInput so the
                        // D-form harness's adapt_arg() can materialize
                        // ListNode / TreeNode from a raw list. Without this,
                        // unannotated Python solutions calling reverse(head)
                        // would crash on .next access.
                        Object typeObj = item.get("type");
                        if (typeObj != null && !typeObj.toString().isBlank()) {
                            ri.setType(typeObj.toString());
                        }
                        runInputs.add(ri);
                    }
                } catch (JsonProcessingException e) {
                    log.warn("Failed to parse inputs JSON for problem example {}, falling back to inputText", tc.getId());
                }
            }
            // Fallback: wrap inputText as single input
            if (runInputs.isEmpty() && tc.getInputText() != null) {
                RunSubmissionDTO.RunInput ri = new RunSubmissionDTO.RunInput();
                ri.setId("0");
                ri.setLabel("input");
                ri.setName("input");
                ri.setValue(tc.getInputText());
                runInputs.add(ri);
            }
            rtc.setInputs(runInputs);
            return rtc;
        }).toList());
        return runDto;
    }

    /**
     * P0-1 {@code test_cases} overload of {@link #buildRunSubmissionDTO(String, String, List)}.
     * <p>
     * Maps {@code TestCase} → {@code RunTestCase} using the same JSON-input parsing
     * as {@code ProblemExample} (the {@code inputs} column is a JSON array of
     * {@code {name, label, value, type}} objects, identical schema across both
     * tables). Uses {@code testOrder} for the user-visible label and
     * {@code testCase.id} (the canonical {@code varchar(40)} id) for the
     * {@code RunTestCase.id} the D-form harness will echo back as
     * {@code RunCaseResult.testCaseId}.
     * <p>
     * Named {@code buildRunSubmissionDTOFromTestCases} (not a plain overload) to
     * avoid Java generic erasure clash with the {@code List<ProblemExample>}
     * overload above.
     */
    private RunSubmissionDTO buildRunSubmissionDTOFromTestCases(String language, String code, List<TestCase> cases) {
        RunSubmissionDTO runDto = new RunSubmissionDTO();
        runDto.setLanguage(language);
        runDto.setCode(code);
        runDto.setTestCases(cases.stream().map(tc -> {
            RunSubmissionDTO.RunTestCase rtc = new RunSubmissionDTO.RunTestCase();
            rtc.setId(tc.getId());
            rtc.setLabel("Case " + tc.getTestOrder());
            rtc.setOutput(tc.getOutputText());

            List<RunSubmissionDTO.RunInput> runInputs = new ArrayList<>();
            if (tc.getInputs() != null && !tc.getInputs().isBlank()) {
                try {
                    List<Map<String, Object>> inputs = objectMapper.readValue(
                            tc.getInputs(), new TypeReference<List<Map<String, Object>>>() {});
                    for (int i = 0; i < inputs.size(); i++) {
                        Map<String, Object> item = inputs.get(i);
                        RunSubmissionDTO.RunInput ri = new RunSubmissionDTO.RunInput();
                        ri.setId(String.valueOf(i));
                        Object nameObj = item.get("name");
                        Object labelObj = item.get("label");
                        String name = (nameObj != null ? nameObj : (labelObj != null ? labelObj : "input")).toString();
                        ri.setLabel(name);
                        ri.setName(name);
                        Object valueObj = item.get("value");
                        ri.setValue(valueObj != null ? valueObj.toString() : "");
                        Object typeObj = item.get("type");
                        if (typeObj != null && !typeObj.toString().isBlank()) {
                            ri.setType(typeObj.toString());
                        }
                        runInputs.add(ri);
                    }
                } catch (JsonProcessingException e) {
                    log.warn("Failed to parse inputs JSON for test_case {}, falling back to inputText", tc.getId());
                }
            }
            if (runInputs.isEmpty() && tc.getInputText() != null) {
                RunSubmissionDTO.RunInput ri = new RunSubmissionDTO.RunInput();
                ri.setId("0");
                ri.setLabel("input");
                ri.setName("input");
                ri.setValue(tc.getInputText());
                runInputs.add(ri);
            }
            rtc.setInputs(runInputs);
            return rtc;
        }).toList());
        return runDto;
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
