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
import java.util.stream.Collectors;

/**
 * Thin facade for code execution.
 * Delegates Docker sandbox lifecycle to SandboxService and per-language logic to CodeExecutionHelper.
 * <p>
 * M1a round-4 (Codex F15): verdict aggregation now delegates to the shared
 * {@link VerdictResolver} so the {@code /run} and {@code /submit} paths
 * cannot disagree on the same case set. The legacy {@code VERDICT_PRIORITY}
 * map has been removed; its ordering was inverse to the adjudicator's
 * severity convention which would have produced {@code Presentation Error}
 * for {Wrong Answer + Presentation Error} on /run but {Wrong Answer} on
 * /submit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeExecutionService {

    private final SandboxService sandboxService;
    private final CodeExecutionHelper helper;
    private final VerdictResolver verdictResolver;

    public RunResultDTO execute(RunSubmissionDTO request, Long problemId, String userId) {
        String language = request.getLanguage().toLowerCase().trim();

        // CR fix (Phase 5.5 #1): validate against the actual executable language
        // set (DFORM_SUPPORTED_LANGUAGES), not the API-advertised
        // SUPPORTED_LANGUAGES. After Form A was deleted, the dispatcher can
        // only run java + python. javascript / c / cpp would have been
        // accepted by SUPPORTED_LANGUAGES and then crashed at the
        // dispatcher with an opaque "unsupported language" exception.
        if (!CodeExecutionHelper.DFORM_SUPPORTED_LANGUAGES.contains(language)) {
            throw new BusinessException(ErrorCode.SUBMISSION_LANGUAGE_UNSUPPORTED,
                    "Unsupported language: " + language + ". Supported: "
                            + CodeExecutionHelper.DFORM_SUPPORTED_LANGUAGES);
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

        String verdict = verdictResolver.reduceWire(
                results.stream().map(RunResultDTO.RunCaseResult::getStatus).collect(Collectors.toList())
        ).wireValue();
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
}
