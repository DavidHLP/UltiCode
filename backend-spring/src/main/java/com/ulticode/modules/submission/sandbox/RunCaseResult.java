package com.ulticode.modules.submission.sandbox;

import com.ulticode.modules.submission.enums.SubmissionStatus;

/**
 * Per-test-case result produced by {@link SandboxExecutor}.
 *
 * <p>This is the <b>port</b>'s output type. The wire DTO
 * {@code RunResultDTO.RunCaseResult} is mapped from this record at the
 * facade boundary (see {@code CodeExecutionService}); the sandbox
 * itself never ships these bytes to the frontend.
 *
 * <h2>Field contract</h2>
 * <ul>
 *   <li>{@code status} — the per-case verdict as a {@link SubmissionStatus}
 *       enum (ADR-001). Never a raw wire string. Callers downstream
 *       convert to the wire value via
 *       {@code SubmissionStatusCodec.toWire}.</li>
 *   <li>{@code elapsedMs} — wall-clock duration of the case in
 *       milliseconds, as reported by the harness. Always non-negative;
 *       {@code 0} when the harness could not measure (e.g. compile
 *       failure that never ran).</li>
 *   <li>{@code memoryBytes} — peak resident-set size in bytes, as
 *       reported by the harness (or by docker {@code --memory} cap
 *       when the harness did not report). Always non-negative.</li>
 *   <li>{@code detail} — free-form short diagnostic string. Used by
 *       the UI to show why a case failed (e.g. the first line of
 *       compiler output, or a
 *       {@code "Timeout after 2000ms"} hint). May be {@code null}.</li>
 *   <li>{@code score} — per-case score in {@code [0.0, 1.0]}.
 *       {@code 1.0} for {@link SubmissionStatus#ACCEPTED} and
 *       {@code 0.0} for any other status. Partial credit problems
 *       may report a fractional value; the executor does not enforce
 *       a value-range invariant.</li>
 * </ul>
 *
 * <h2>What is NOT here</h2>
 * <p>Display-only fields (pre-formatted {@code "12ms"} /
 * {@code "22.0MB"}, {@code runId}, {@code caseLabel}, raw
 * {@code output} / {@code expectedOutput} / {@code inputs}) live on
 * the wire DTO, not on the port. The port's job is verdict + raw
 * numbers; presentation is the DTO's job.
 *
 * @see SandboxExecutor#run(SandboxJob, TestCase)
 * @see BatchRunResult
 */
public record RunCaseResult(
        SubmissionStatus status,
        long elapsedMs,
        long memoryBytes,
        String detail,
        double score
) {

    /**
     * Convenience: build an {@link SubmissionStatus#ACCEPTED} result
     * with score 1.0 and an empty detail.
     */
    public static RunCaseResult accepted(long elapsedMs, long memoryBytes) {
        return new RunCaseResult(SubmissionStatus.ACCEPTED, elapsedMs, memoryBytes, null, 1.0);
    }

    /**
     * Convenience: build a rejected result for a non-accepted status
     * with score 0.0 and the given detail.
     */
    public static RunCaseResult rejected(SubmissionStatus status, String detail,
                                         long elapsedMs, long memoryBytes) {
        if (status == SubmissionStatus.ACCEPTED) {
            throw new IllegalArgumentException(
                    "rejected() is for non-accepted statuses; use accepted() for ACCEPTED");
        }
        return new RunCaseResult(status, elapsedMs, memoryBytes, detail, 0.0);
    }
}
