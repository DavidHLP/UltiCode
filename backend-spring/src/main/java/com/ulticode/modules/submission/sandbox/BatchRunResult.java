package com.ulticode.modules.submission.sandbox;

import java.util.List;

/**
 * Per-batch result produced by {@link SandboxExecutor#runBatch}.
 *
 * <p>The port deliberately does not carry an "overall verdict" field
 * here — reducing a list of per-case results into one verdict is a
 * caller concern, owned by
 * {@code com.ulticode.modules.submission.service.VerdictResolver} (see
 * ADR-001 §2.4). Keeping the port free of verdict-reduction logic
 * means a single-case run and a multi-case run produce the same shape
 * of per-case data, and the reducer never has to special-case "the
 * batch wrapper said X already, ignore the cases".
 *
 * <h2>Length / order contract</h2>
 * <p>Implementations of {@link SandboxExecutor#runBatch} MUST return
 * a {@code BatchRunResult} whose {@link #cases()} list has the same
 * length and order as the input test-case list. On infrastructure
 * failure that prevents any case from running, implementations fill
 * the list with one
 * {@link SubmissionStatus#SANDBOX_ERROR} per input test case (see
 * ADR-002 §2.5) so the caller can rely on positional alignment.
 */
public record BatchRunResult(List<RunCaseResult> cases) {
    public BatchRunResult {
        cases = List.copyOf(cases);
    }
}
