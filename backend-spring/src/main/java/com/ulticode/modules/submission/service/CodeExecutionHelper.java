package com.ulticode.modules.submission.service;

import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;

import java.util.List;
import java.util.Set;

/**
 * Helper service for the D-form (LeetCode/HackerRank) sandbox dispatch.
 *
 * <p>Owns three responsibilities:
 * <ol>
 *   <li>Build the {@code input.json} payload the pre-compiled harness
 *       reads from {@code /job/input.json}.
 *   <li>Parse the JSON envelope the harness writes to stdout and map
 *       per-case verdicts into {@link RunResultDTO.RunCaseResult}.
 *   <li>Small utility helpers (sanitize, normalize, build empty
 *       result) that both the dispatcher and downstream services need.
 * </ol>
 *
 * <p>No Docker, security, or per-language wrapper code lives here —
 * the harness in the sandbox image does that. This is pure data
 * shaping against the wire contract.
 */
public interface CodeExecutionHelper {

    /**
     * Languages the codebase advertises in the API. Kept as a Set
     * so the controller can validate before reaching the dispatcher.
     * (The actual set of languages whose execution paths are
     * implemented lives in {@link #DFORM_SUPPORTED_LANGUAGES}.)
     */
    Set<String> SUPPORTED_LANGUAGES = Set.of(
            "javascript", "python", "java", "c", "cpp"
    );

    /**
     * Languages the D-form harness actually supports. Smaller than
     * {@link #SUPPORTED_LANGUAGES} because:
     * <ul>
     *   <li>JavaScript isn't part of the migration (no JS harness yet)
     *   <li>C and C++ D-form dispatch needs a complete harness
     *       implementation (the {@code docker/sandbox/harness/{c,cpp}/}
     *       trees are Phase 1 smoke skeletons that don't read
     *       {@code input.json}). Re-add after envelope-producing
     *       C/C++ harnesses ship.
     * </ul>
     */
    Set<String> DFORM_SUPPORTED_LANGUAGES = Set.of("java", "python");

    String extractFunctionName(String code, String keyword);

    String normalizeOutput(String output);

    long parseRuntimeMs(String runtime);

    String sanitizeSandboxOutput(String output);

    RunResultDTO emptyResult(Long problemId, String userId);

    RunResultDTO.RunCaseResult buildCaseResult(RunSubmissionDTO.RunTestCase testCase,
                                               String runId, String userId,
                                               String status, long runtimeMs,
                                               String output, String detail,
                                               double memoryMb);

    /**
     * Build the single-case {@code input.json} payload that the D-form
     * harness reads from {@code /job/input.json}.
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
     * as {@code testCases}). On envelope parse failure, returns one
     * Runtime Error per test case so the caller still gets a
     * well-formed list back.
     */
    List<RunResultDTO.RunCaseResult> parseDEnvelope(String stdout,
                                                     List<RunSubmissionDTO.RunTestCase> testCases,
                                                     String runId, String userId);
}
