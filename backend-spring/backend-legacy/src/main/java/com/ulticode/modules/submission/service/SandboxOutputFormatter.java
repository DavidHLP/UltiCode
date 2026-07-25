package com.ulticode.modules.submission.service;

import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;

/**
 * Display and DTO-construction helpers for the D-form sandbox pipeline.
 *
 * <p>Display and DTO-construction seam for the D-form sandbox pipeline
 * (the C4 split collapsed the old forwarding {@code CodeExecutionHelper}
 * facade so display concerns own one home). Owns:
 * <ul>
 *   <li>{@link #sanitizeSandboxOutput} — strip docker / OCI runtime lines
 *       from harness stdout so the user-facing {@code detail} string stays
 *       free of infrastructure noise.</li>
 *   <li>{@link #emptyResult} — produce a {@code System Error} result shell
 *       for the dispatcher when nothing ran.</li>
 *   <li>{@link #buildCaseResult} — assemble a single {@link RunResultDTO.RunCaseResult}
 *       from harness-reported values plus the original test-case.</li>
 *   <li>{@link #parseRuntimeMs} — parse the raw sandbox runtime wire formats
 *       ({@code "Nms"}, {@code "N.Ns"}, bare numbers) the dispatcher and
 *       sandbox executor see.</li>
 * </ul>
 *
 * @author ulticode
 */
public interface SandboxOutputFormatter {

    /**
     * Parse a sandbox runtime wire string to milliseconds. Unlike
     * {@link com.ulticode.modules.queue.port.VerdictMetricsParser#parseRuntimeMs},
     * this version serves the raw sandbox-output formats the dispatcher and
     * sandbox executor see &mdash; the {@code "s"} suffix (seconds -&gt; millis
     * via {@code Double} math) and fractional milliseconds (e.g.
     * {@code "42.5ms"}). The queue-side parser only accepts integer
     * {@code "Nms"} payloads, so the two are NOT interchangeable; routing this
     * onto {@code VerdictMetricsParser} would silently drop runtime data for
     * those formats.
     *
     * @param runtime raw sandbox runtime string (may be {@code null})
     * @return milliseconds, or {@code 0L} when the input is blank or unparseable
     */
    long parseRuntimeMs(String runtime);

    /**
     * Format raw sandbox / harness output for the user-facing
     * {@code detail} string. Strips docker / OCI runtime infrastructure
     * lines and collapses an empty result to {@code "Runtime error"}.
     *
     * <p>This is purely a display formatter. The failure oracle lives in
     * {@code com.ulticode.modules.submission.sandbox.SandboxOutcomeClassifier},
     * which sees the raw stdout (including docker / OCI lines) so real
     * infra failures stop being scrubbed and flattened to a generic
     * "Runtime error".
     *
     * @param output raw harness stdout
     * @return formatted display string, or {@code "Runtime error"} when
     *         nothing usable survives the scrub
     */
    String sanitizeSandboxOutput(String output);

    /**
     * Build an empty {@link RunResultDTO} carrying the {@code System Error}
     * verdict. Used by the dispatcher when the harness never produced a
     * result envelope.
     *
     * @param problemId problem id stamped on the result
     * @param userId    user id stamped on the result
     * @return empty result DTO with verdict {@code "System Error"}
     */
    RunResultDTO emptyResult(Long problemId, String userId);

    /**
     * Build a single per-case result from harness-reported values plus the
     * original test case.
     *
     * @param testCase   the test case this result corresponds to
     * @param runId      run id
     * @param userId     user id
     * @param status     per-case status string from the harness
     * @param runtimeMs  legacy per-case runtime in milliseconds
     * @param output     per-case actual output (may be truncated/null)
     * @param detail     per-case detail message (already sanitised)
     * @param memoryMb   peak memory usage in MiB
     * @param elapsedUs  precise elapsed microseconds (0 means harness
     *                   didn't report; the legacy ms value is used)
     * @param cpuMs      CPU time in milliseconds (0 means harness didn't report)
     * @return assembled per-case result
     */
    RunResultDTO.RunCaseResult buildCaseResult(RunSubmissionDTO.RunTestCase testCase,
                                               String runId, String userId,
                                               String status, long runtimeMs,
                                               String output, String detail,
                                               double memoryMb,
                                               long elapsedUs, long cpuMs);
}