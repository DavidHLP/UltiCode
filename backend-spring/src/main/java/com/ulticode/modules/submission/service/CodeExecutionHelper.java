package com.ulticode.modules.submission.service;

import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;

import java.util.List;
import java.util.Set;

/**
 * Helper service for code execution: per-language wrappers, result parsing, and utilities.
 * No Docker or security concerns -- pure logic.
 */
public interface CodeExecutionHelper {

    Set<String> SUPPORTED_LANGUAGES = Set.of(
            "javascript", "python", "java", "c", "cpp"
    );

    String buildWrapperScript(String language, String code, List<RunSubmissionDTO.RunTestCase> testCases);

    String buildBatchInputsJson(List<RunSubmissionDTO.RunTestCase> testCases);

    List<RunResultDTO.RunCaseResult> parseBatchResults(String stdout,
                                                        List<RunSubmissionDTO.RunTestCase> testCases,
                                                        String runId, String userId);

    String wrapJavaScript(String code);

    String wrapPython(String code);

    String wrapJava(String code);

    String extractFunctionName(String code, String keyword);

    String buildInputsJson(RunSubmissionDTO.RunTestCase testCase);

    String parseInputValue(String value);

    String normalizeOutput(String output);

    long parseRuntimeMs(String runtime);

    String sanitizeSandboxOutput(String output);

    RunResultDTO emptyResult(Long problemId, String userId);

    RunResultDTO.RunCaseResult buildCaseResult(RunSubmissionDTO.RunTestCase testCase,
                                               String runId, String userId,
                                               String status, long runtimeMs,
                                               String output, String detail,
                                               double memoryMb);
}
