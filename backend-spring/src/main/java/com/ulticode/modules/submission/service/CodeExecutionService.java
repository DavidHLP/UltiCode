package com.ulticode.modules.submission.service;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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

        String verdict = passedCases == testCases.size() ? "Accepted" : "Wrong Answer";
        long totalRuntimeMs = results.stream()
                .mapToLong(r -> helper.parseRuntimeMs(r.getRuntime()))
                .sum();

        return RunResultDTO.builder()
                .id(runId)
                .problemId(String.valueOf(problemId))
                .userId(userId)
                .verdict(verdict)
                .runtime(totalRuntimeMs + "ms")
                .memory(results.stream()
                        .map(RunResultDTO.RunCaseResult::getMemory)
                        .max(String::compareTo)
                        .orElse("0.0MB"))
                .cases(results)
                .passedCases(passedCases)
                .totalCases(testCases.size())
                .build();
    }
}
