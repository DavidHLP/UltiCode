package com.ulticode.modules.queue.processor;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.problem.entity.TestCase;
import com.ulticode.modules.problem.mapper.TestCaseMapper;
import com.ulticode.modules.queue.config.QueueConfig;
import com.ulticode.modules.queue.constants.QueueConstants;
import com.ulticode.modules.queue.dto.JobStatusDTO;
import com.ulticode.modules.queue.job.JudgeJob;
import com.ulticode.modules.queue.job.JobProcessor;
import com.ulticode.modules.queue.service.QueueService;
import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.service.CodeExecutionService;
import com.ulticode.modules.submission.service.SubmissionService;
import com.ulticode.modules.websocket.contest.dto.SubmissionResultPayload;
import com.ulticode.modules.websocket.service.RealtimeService;
import com.ulticode.modules.contest.entity.ContestSubmission;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Judge worker that polls the Redis judge queue and processes submissions.
 *
 * <p>Wires together QueueService, CodeExecutionService, SubmissionService,
 * and RealtimeService to form the complete judging pipeline:
 *
 * <ol>
 *   <li>Poll job from Redis queue
 *   <li>Set submission status to "Judging"
 *   <li>Load test cases, build RunSubmissionDTO, execute via Docker sandbox
 *   <li>Determine verdict with priority ordering (RE > MLE > TLE > WA > PE > Accepted)
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

    private static final Map<String, Integer> VERDICT_PRIORITY = Map.of(
            "Runtime Error", 5,
            "Memory Limit Exceeded", 4,
            "Time Limit Exceeded", 3,
            "Wrong Answer", 2,
            "Presentation Error", 1,
            "Accepted", 0
    );

    private final QueueService queueService;
    private final CodeExecutionService codeExecutionService;
    private final SubmissionService submissionService;
    private final RealtimeService realtimeService;
    private final ContestSubmissionMapper contestSubmissionMapper;
    private final TestCaseMapper testCaseMapper;
    private final QueueConfig queueConfig;
    private final ObjectMapper objectMapper;

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
     * Process a judge job: execute code, determine verdict, write result, push WebSocket.
     */
    public void processJob(JudgeJob job) {
        String submissionId = job.getSubmissionId();
        String problemId = job.getProblemId();
        String userId = job.getUserId();

        try {
            // Mark as judging
            submissionService.updateSubmissionResult(submissionId, "Judging", 0, null, null);

            // Load test cases
            List<TestCase> testCases = testCaseMapper.findByProblemIdOrderByOrder(Long.parseLong(problemId));
            if (testCases == null || testCases.isEmpty()) {
                log.warn("No test cases found for problem {}", problemId);
                submissionService.updateSubmissionResult(submissionId, "System Error", 0, 0.0, null);
                pushResult(userId, submissionId, problemId, "System Error", 0, 0L, null);
                return;
            }

            // Build RunSubmissionDTO
            RunSubmissionDTO runDto = buildRunSubmissionDTO(job, testCases);

            // Execute
            RunResultDTO result = codeExecutionService.execute(runDto, Long.parseLong(problemId), userId);

            // Determine verdict
            String verdict = determineVerdict(result.getCases());

            // Compute max runtime and memory across all cases
            long maxRuntimeMs = 0;
            double maxMemoryMb = 0.0;
            for (RunResultDTO.RunCaseResult caseResult : result.getCases()) {
                maxRuntimeMs = Math.max(maxRuntimeMs, parseRuntimeMs(caseResult.getRuntime()));
                maxMemoryMb = Math.max(maxMemoryMb, parseMemoryMb(caseResult.getMemory()));
            }

            // Build test case details
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

            // Write result
            submissionService.updateSubmissionResult(submissionId, verdict, (int) maxRuntimeMs, maxMemoryMb, testCaseDetails);

            // Push WebSocket
            long memoryBytes = (long) (maxMemoryMb * 1024 * 1024);
            String contestId = findContestIdBySubmissionId(submissionId);
            pushResult(userId, submissionId, problemId, verdict, (int) maxRuntimeMs, memoryBytes, contestId);

        } catch (Exception e) {
            log.error("Failed to process judge job for submission {}", submissionId, e);
            throw new RuntimeException("Judge processing failed for submission " + submissionId, e);
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
     * Determine the final verdict from case results using priority ordering.
     * Priority: Runtime Error > Memory Limit Exceeded > Time Limit Exceeded
     * > Wrong Answer > Presentation Error > Accepted.
     */
    String determineVerdict(List<RunResultDTO.RunCaseResult> cases) {
        if (cases == null || cases.isEmpty()) {
            return "System Error";
        }

        int highestPriority = -1;
        String worstVerdict = "Accepted";

        for (RunResultDTO.RunCaseResult caseResult : cases) {
            int priority = VERDICT_PRIORITY.getOrDefault(caseResult.getStatus(), 0);
            if (priority > highestPriority) {
                highestPriority = priority;
                worstVerdict = caseResult.getStatus();
            }
        }

        return worstVerdict;
    }

    /**
     * Parse runtime string like "123ms" to milliseconds.
     */
    long parseRuntimeMs(String runtime) {
        if (runtime == null || runtime.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(runtime.replace("ms", "").trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Parse memory string like "4.2MB" to megabytes.
     */
    double parseMemoryMb(String memory) {
        if (memory == null || memory.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(memory.replace("MB", "").trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private RunSubmissionDTO buildRunSubmissionDTO(JudgeJob job, List<TestCase> testCases) {
        RunSubmissionDTO runDto = new RunSubmissionDTO();
        runDto.setLanguage(job.getLanguage());
        runDto.setCode(job.getCode());
        runDto.setTestCases(testCases.stream().map(tc -> {
            RunSubmissionDTO.RunTestCase rtc = new RunSubmissionDTO.RunTestCase();
            rtc.setId(String.valueOf(tc.getId()));
            rtc.setLabel("Case " + tc.getTestOrder());
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
                        runInputs.add(ri);
                    }
                } catch (JsonProcessingException e) {
                    log.warn("Failed to parse inputs JSON for test case {}, falling back to inputText", tc.getId());
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

    private void pushResult(String userId, String submissionId, String problemId,
                            String status, int timeUsed, long memoryUsed, String contestId) {
        SubmissionResultPayload payload = SubmissionResultPayload.of(
                submissionId, contestId, problemId, userId, status, 0, timeUsed, memoryUsed);
        realtimeService.emitSubmissionResult(userId, payload);
    }

    private String findContestIdBySubmissionId(String submissionId) {
        ContestSubmission cs = contestSubmissionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ContestSubmission>()
                        .eq(ContestSubmission::getSubmissionId, submissionId));
        return cs != null ? cs.getContestId() : null;
    }
}
