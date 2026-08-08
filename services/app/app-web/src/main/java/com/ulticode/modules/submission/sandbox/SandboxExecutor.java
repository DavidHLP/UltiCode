package com.ulticode.modules.submission.sandbox;

import java.util.List;

/**
 * Hexagonal port (ADR-002) for executing user code against test cases in
 * a sandboxed environment.
 *
 * <p>This is the top-level boundary the rest of the system (judge worker,
 * code execution facade, etc.) talks to. It deliberately knows nothing
 * about Docker, firecracker, or any other isolation technology — those
 * are adapter concerns, swapped at deploy time via
 * {@code @ConditionalOnProperty(name = "sandbox.executor", ...)}.
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>{@link #run(SandboxJob, TestCase)} returns one {@link RunCaseResult}
 *       for the given single test case.</li>
 *   <li>{@link #runBatch(SandboxJob, List)} returns one {@link BatchRunResult}
 *       whose {@code cases} list has the same order and length as the
 *       input list. The caller (currently {@code VerdictResolver}) is
 *       responsible for reducing the per-case results into an overall
 *       verdict — the port does not pick a winner on its own.</li>
 *   <li>The result's {@link RunCaseResult#status()} is the
 *       {@code SubmissionStatus} enum (ADR-001) — never a raw wire
 *       string. Wire-level string conversion happens at the DTO
 *       boundary, not inside the sandbox.</li>
 *   <li>Cross-language infrastructure failures (e.g. Docker daemon
 *       fork pressure) surface as
 *       {@link com.ulticode.domain.submission.enums.SubmissionStatus#SANDBOX_ERROR}
 *       rather than throwing — the per-case list must always be the
 *       same length as the input list so the caller can rely on
 *       positional alignment.</li>
 * </ul>
 *
 * <h2>What this port does NOT do</h2>
 * <ul>
 *   <li>Materialize user code to a workspace — that is each
 *       {@link LanguageProfile#materializeWorkspace} responsibility.</li>
 *   <li>Build the per-language docker command — that is each
 *       {@link LanguageProfile#dockerCommand} responsibility.</li>
 *   <li>Classify language-specific failures (e.g. Java compile errors) —
 *       that is each {@link LanguageProfile#isCompileFailure}
 *       responsibility. (See ADR-002 §2.4.)</li>
 * </ul>
 *
 * @see SandboxJob
 * @see TestCase
 * @see RunCaseResult
 * @see BatchRunResult
 * @see LanguageProfile
 */
public interface SandboxExecutor {

    /**
     * Execute a single test case.
     *
     * @param job       immutable per-run job descriptor; carries the
     *                  language, code, limits, and ADR-003 generation
     *                  fence fields.
     * @param testCase  one test case to run against.
     * @return one {@link RunCaseResult} — never {@code null}.
     */
    RunCaseResult run(SandboxJob job, TestCase testCase);

    /**
     * Execute a batch of test cases as a single sandbox invocation.
     * Implementations may exploit batch-level optimizations (one
     * container, one harness call) but must preserve the 1:1 input /
     * output length and order contract.
     *
     * @param job    immutable per-run job descriptor.
     * @param cases  test cases to run; must be non-null and non-empty
     *               (callers validate). Order is preserved.
     * @return a {@link BatchRunResult} whose {@code cases} has the
     *         same length and order as the input list.
     */
    BatchRunResult runBatch(SandboxJob job, List<TestCase> cases);
}
