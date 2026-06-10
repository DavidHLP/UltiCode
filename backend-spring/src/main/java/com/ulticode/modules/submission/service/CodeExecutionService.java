package com.ulticode.modules.submission.service;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Thin facade for code execution.
 * Delegates Docker sandbox lifecycle to SandboxService and per-language logic to CodeExecutionHelper.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeExecutionService {

    private final SandboxService sandboxService;
    private final CodeExecutionHelper helper;

    private static final Map<String, Integer> VERDICT_PRIORITY = new LinkedHashMap<>();

    static {
        VERDICT_PRIORITY.put("System Error", 0);
        VERDICT_PRIORITY.put("Compile Error", 1);
        VERDICT_PRIORITY.put("Runtime Error", 2);
        VERDICT_PRIORITY.put("Time Limit Exceeded", 3);
        VERDICT_PRIORITY.put("Memory Limit Exceeded", 4);
        VERDICT_PRIORITY.put("Output Limit Exceeded", 5);
        VERDICT_PRIORITY.put("Presentation Error", 6);
        VERDICT_PRIORITY.put("Wrong Answer", 7);
        VERDICT_PRIORITY.put("Accepted", 8);
        VERDICT_PRIORITY.put("Judging", 9);
        VERDICT_PRIORITY.put("Pending", 10);
    }

    public RunResultDTO execute(RunSubmissionDTO request, Long problemId, String userId) {
        String language = request.getLanguage().toLowerCase().trim();

        if (!CodeExecutionHelper.SUPPORTED_LANGUAGES.contains(language)) {
            throw new BusinessException(ErrorCode.SUBMISSION_LANGUAGE_UNSUPPORTED,
                    "Unsupported language: " + language + ". Supported: " + CodeExecutionHelper.SUPPORTED_LANGUAGES);
        }

        List<RunSubmissionDTO.RunTestCase> testCases = request.getTestCases();
        if (testCases == null || testCases.isEmpty()) {
            return helper.emptyResult(problemId, userId);
        }

        String runId = UUID.randomUUID().toString();
        List<RunResultDTO.RunCaseResult> results;
        int passedCases;

        if (testCases.size() == 1) {
            RunResultDTO.RunCaseResult caseResult = sandboxService.executeInSandbox(
                    language, request.getCode(), testCases.get(0), runId, userId);
            results = new ArrayList<>();
            results.add(caseResult);
            passedCases = "Accepted".equals(caseResult.getStatus()) ? 1 : 0;
        } else {
            results = sandboxService.executeBatch(language, request.getCode(), testCases, runId, userId);
            passedCases = (int) results.stream()
                    .filter(r -> "Accepted".equals(r.getStatus()))
                    .count();
        }

        String verdict = determineVerdict(results);
        long totalRuntimeMs = results.stream()
                .mapToLong(r -> helper.parseRuntimeMs(r.getRuntime()))
                .sum();
        double maxMemoryMb = results.stream()
                .map(RunResultDTO.RunCaseResult::getMemoryMb)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);

        return RunResultDTO.builder()
                .id(runId)
                .problemId(problemId)
                .userId(userId)
                .verdict(verdict)
                .runtime(totalRuntimeMs + "ms")
                .runtimeMs(totalRuntimeMs)
                .memory(String.format("%.1fMB", maxMemoryMb))
                .memoryMb(maxMemoryMb)
                .cases(results)
                .passedCases(passedCases)
                .totalCases(testCases.size())
                .build();
    }

    private String determineVerdict(List<RunResultDTO.RunCaseResult> results) {
        if (results == null || results.isEmpty()) {
            return "Pending";
        }
        return results.stream()
                .min((a, b) -> {
                    int pa = VERDICT_PRIORITY.getOrDefault(a.getStatus(), Integer.MAX_VALUE);
                    int pb = VERDICT_PRIORITY.getOrDefault(b.getStatus(), Integer.MAX_VALUE);
                    return Integer.compare(pa, pb);
                })
                .map(RunResultDTO.RunCaseResult::getStatus)
                .orElse("Pending");
    }
}
