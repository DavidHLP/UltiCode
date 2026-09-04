package com.ulticode.modules.submission.sandbox;

import com.ulticode.domain.submission.enums.SubmissionStatus;

import java.util.List;

/**
 * Per-test-case result produced by {@link SandboxExecutor}.
 *
 * <p>This is the sandbox port's output type. The runtime-private
 * {@code JudgeRunResponse.RunCaseResult} is mapped from this record at the
 * runtime facade seam; the sandbox itself never ships bytes to the frontend.
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
 *       failure that never ran). Legacy ms-truncated value.</li>
 *   <li>{@code elapsedUs} — precise wall-clock duration in microseconds
 *       (ADR-002 §8). Preferred over {@code elapsedMs} for display since
 *       the ms value truncates 0–999µs to {@code 0ms}. {@code 0} for
 *       older harnesses that do not emit {@code elapsed_us}.</li>
 *   <li>{@code cpuMs} — CPU time (user + sys) the user code consumed, in
 *       milliseconds (ADR-002 §8). Used for fair cross-language
 *       comparison; TLE is still judged on wall-clock. {@code 0} for
 *       older harnesses.</li>
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
 *   <li>{@code output} — M2a-round-2 (codex review F3): the actual
 *       stdout (or whatever the harness writes for the case). The
 *       port preserves it so the DTO can hand it to
 *       {@code JudgeWorkerProcessor}, which persists it on the
 *       {@code submission_cases} row. May be {@code null} when the
 *       harness did not report it (e.g. compile failure that never
 *       ran user code).</li>
 *   <li>{@code expectedOutput} — M2a-round-2: the expected output
 *       the harness compared against. Carried through so the UI can
 *       show "got X / expected Y" for {@code WRONG_ANSWER} cases.
 *       May be {@code null} when the test case has no expected
 *       output or the harness did not echo it back.</li>
 *   <li>{@code inputs} — M2a-round-2: the input parameters the
 *       harness bound to the solution. Used by the judge persistence
 *       path to record what the case actually ran with. May be
 *       {@code null} for legacy callers that do not supply inputs.</li>
 * </ul>
 *
 * @see SandboxExecutor#run(SandboxJob, TestCase)
 * @see BatchRunResult
 */
public record RunCaseResult(
        SubmissionStatus status,
        long elapsedMs,
        long memoryBytes,
        long elapsedUs,
        long cpuMs,
        String detail,
        double score,
        String output,
        String expectedOutput,
        List<TestCase.Input> inputs
) {

    /**
     * Canonical constructor — full 10-arg form used by the executor when
     * it has the complete measurement set from the envelope. Prefer the
     * static factories below for hand-built results (tests, adapters).
     */
    public RunCaseResult(SubmissionStatus status, long elapsedMs, long memoryBytes,
                         long elapsedUs, long cpuMs, String detail, double score,
                         String output, String expectedOutput,
                         List<TestCase.Input> inputs) {
        this.status = status;
        this.elapsedMs = elapsedMs;
        this.memoryBytes = memoryBytes;
        this.elapsedUs = elapsedUs;
        this.cpuMs = cpuMs;
        this.detail = detail;
        this.score = score;
        this.output = output;
        this.expectedOutput = expectedOutput;
        this.inputs = inputs;
    }

    /**
     * Convenience: build an {@link SubmissionStatus#ACCEPTED} result
     * with score 1.0 and no per-case output. Use
     * {@link #acceptedWithOutput} when the harness reported the
     * actual stdout.
     */
    public static RunCaseResult accepted(long elapsedMs, long memoryBytes) {
        return new RunCaseResult(SubmissionStatus.ACCEPTED, elapsedMs, memoryBytes,
                0L, 0L, null, 1.0, null, null, null);
    }

    /**
     * Convenience: build an {@link SubmissionStatus#ACCEPTED} result
     * with the harness's reported stdout, expected output, and
     * inputs preserved.
     */
    public static RunCaseResult acceptedWithOutput(long elapsedMs, long memoryBytes,
                                                   String output, String expectedOutput,
                                                   List<TestCase.Input> inputs) {
        return new RunCaseResult(SubmissionStatus.ACCEPTED, elapsedMs, memoryBytes,
                0L, 0L, null, 1.0, output, expectedOutput, inputs);
    }

    /**
     * Convenience: build a rejected result for a non-accepted status
     * with score 0.0 and the given detail. Failure paths rarely have
     * a useful {@code output}; the convenience leaves it null.
     */
    public static RunCaseResult rejected(SubmissionStatus status, String detail,
                                         long elapsedMs, long memoryBytes) {
        if (status == SubmissionStatus.ACCEPTED) {
            throw new IllegalArgumentException(
                    "rejected() is for non-accepted statuses; use accepted() for ACCEPTED");
        }
        return new RunCaseResult(status, elapsedMs, memoryBytes,
                0L, 0L, detail, 0.0, null, null, null);
    }

    /**
     * Convenience: build a rejected result that still preserves the
     * harness's reported output (e.g. {@code WRONG_ANSWER} where the
     * "got vs expected" comparison matters to the UI).
     */
    public static RunCaseResult rejectedWithOutput(SubmissionStatus status, String detail,
                                                   long elapsedMs, long memoryBytes,
                                                   String output, String expectedOutput,
                                                   List<TestCase.Input> inputs) {
        if (status == SubmissionStatus.ACCEPTED) {
            throw new IllegalArgumentException(
                    "rejectedWithOutput() is for non-accepted statuses; use acceptedWithOutput() for ACCEPTED");
        }
        return new RunCaseResult(status, elapsedMs, memoryBytes,
                0L, 0L, detail, 0.0, output, expectedOutput, inputs);
    }
}
