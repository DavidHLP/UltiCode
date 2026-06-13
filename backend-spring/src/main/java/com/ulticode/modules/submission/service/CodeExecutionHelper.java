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

    // ── D-form (LeetCode/HackerRank harness) ─────────────────────────────────
    // The legacy buildWrapperScript / buildBatchInputsJson / parseBatchResults
    // trio above builds a per-request bash wrapper. D-form instead pre-compiles
    // the harness into the sandbox image (docker/sandbox/harness/{lang}/) and
    // ships a single static input.json to it. These three methods produce
    // and parse that contract.

    /**
     * Build the single-case {@code input.json} payload that the D-form
     * harness reads from {@code /job/input.json}. Mirrors the Java harness's
     * {@link com.ulticode.modules.submission.dto.InputSpecDTO} contract.
     *
     * @param perCaseTimeoutMs soft timeout forwarded to the harness
     *                         (Thread.interrupt inside the harness worker)
     */
    String buildDInputsJson(RunSubmissionDTO.RunTestCase testCase, long perCaseTimeoutMs);

    /**
     * Build the multi-case {@code input.json} payload. Each test case is
     * a {@code cases[]} entry with the same shape as the single-case form.
     */
    String buildDBatchInputsJson(List<RunSubmissionDTO.RunTestCase> testCases, long perCaseTimeoutMs);

    /**
     * Parse the JSON envelope the harness wrote to stdout. Returns one
     * {@link RunResultDTO.RunCaseResult} per test case (in the same order
     * as {@code testCases}). On envelope parse failure, returns a single
     * Runtime Error result for the whole batch so the caller still gets
     * a well-formed list back.
     */
    List<RunResultDTO.RunCaseResult> parseDEnvelope(String stdout,
                                                     List<RunSubmissionDTO.RunTestCase> testCases,
                                                     String runId, String userId);

    /**
     * Languages the D-form harness currently supports. Smaller than
     * {@link #SUPPORTED_LANGUAGES} because JavaScript isn't part of the
     * Phase 3 migration (D-form has no JS harness yet).
     */
    java.util.Set<String> DFORM_SUPPORTED_LANGUAGES = java.util.Set.of("java", "python", "c", "cpp");
}
