package com.ulticode.modules.submission.service;

import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;

import java.util.List;

/**
 * Codec for the D-form harness wire contract — both the input side
 * ({@code /job/input.json}) and the output side (the JSON envelope the
 * harness writes to stdout).
 *
 * <p>Extracted out of {@link CodeExecutionHelper} in the C4 split because
 * the envelope parser ({@link #parseDEnvelope}) was the heaviest method
 * (75 lines of state-machine logic) and three concerns shared one name:
 * <ul>
 *   <li>envelope parse-and-decode (this interface),</li>
 *   <li>display-string formatting (lives in {@link SandboxOutputFormatter}),</li>
 *   <li>DTO construction (also in {@link SandboxOutputFormatter}).</li>
 * </ul>
 *
 * <p>Harness protocol changes (input.json schema, envelope fields, exit
 * codes) touch only this interface — the surface area for diffs shrinks
 * from nine methods to three.
 *
 * @author ulticode
 */
public interface DFormEnvelopeCodec {

    /**
     * Build the single-case {@code input.json} payload that the D-form
     * harness reads from {@code /job/input.json}.
     *
     * @param testCase          the single test case to ship
     * @param perCaseTimeoutMs  soft per-case timeout forwarded to the harness
     *                          (Thread.interrupt inside the harness worker).
     *                          Equals the problem's per-case time limit.
     * @param memoryLimitBytes  hard per-case memory ceiling forwarded to the
     *                          harness so it can self-report Memory Limit
     *                          Exceeded (ADR-002 §8). 0 / negative disables
     *                          the harness-level MLE check (backend still
     *                          enforces via docker {@code --memory}).
     * @return JSON-encoded payload string for {@code /job/input.json}
     */
    String buildDInputsJson(RunSubmissionDTO.RunTestCase testCase,
                            long perCaseTimeoutMs, long memoryLimitBytes);

    /**
     * Build the multi-case {@code input.json} payload. Each test case is
     * a {@code cases[]} entry with the same shape as the single-case form.
     *
     * @param testCases         the test cases to ship (must be non-null)
     * @param perCaseTimeoutMs  per-case soft timeout
     * @param memoryLimitBytes  per-case memory ceiling
     * @return JSON-encoded payload string for {@code /job/input.json}
     */
    String buildDBatchInputsJson(List<RunSubmissionDTO.RunTestCase> testCases,
                                 long perCaseTimeoutMs, long memoryLimitBytes);

    /**
     * Parse the JSON envelope the harness wrote to stdout. Returns one
     * {@link RunResultDTO.RunCaseResult} per test case (in the same order
     * as {@code testCases}). On envelope parse failure, returns one
     * Runtime Error per test case so the caller still gets a
     * well-formed list back.
     *
     * @param stdout     raw harness stdout (may contain JVM warning lines
     *                   before the JSON object — the codec strips them)
     * @param testCases  test cases the harness ran against (drives result length)
     * @param runId      run id stamped on each per-case result
     * @param userId     user id stamped on each per-case result
     * @return list of per-case results, one per test case
     */
    List<RunResultDTO.RunCaseResult> parseDEnvelope(String stdout,
                                                     List<RunSubmissionDTO.RunTestCase> testCases,
                                                     String runId, String userId);
}