package com.ulticode.modules.submission.sandbox.adapter;

import com.ulticode.modules.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.sandbox.BatchRunResult;
import com.ulticode.modules.submission.sandbox.RunCaseResult;
import com.ulticode.modules.submission.sandbox.SandboxExecutor;
import com.ulticode.modules.submission.sandbox.SandboxJob;
import com.ulticode.modules.submission.sandbox.TestCase;
import com.ulticode.modules.submission.port.JudgingLanguageSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-memory {@link SandboxExecutor} for tests and offline development
 * (ADR-002 §2.3).
 *
 * <p>Routes on simple markers in the user code (case-insensitive
 * substring or {@code // verdict: STATUS_NAME} annotation) so a unit
 * test can write:
 *
 * <pre>{@code
 *   executor.run(jobFor("java", "return 0; // verdict: ACCEPTED"), testCase);
 *   // → RunCaseResult with SubmissionStatus.ACCEPTED
 *
 *   executor.run(jobFor("java", "throw new RuntimeException(); // verdict: RUNTIME_ERROR"),
 *                testCase);
 *   // → RunCaseResult with SubmissionStatus.RUNTIME_ERROR
 * }</pre>
 *
 * <h2>Routing rules</h2>
 * <ol>
 *   <li>Explicit marker wins: look for
 *       {@code // verdict: <NAME>} (Python: {@code # verdict: <NAME>})
 *       and map the name through
 *       {@link SubmissionStatus#valueOf(String)}.</li>
 *   <li>Fallback keywords (case-insensitive substring in
 *       {@code job.code()}):
 *       <ul>
 *         <li>{@code "infinite loop"} or {@code "while(true)"} →
 *             {@code TIME_LIMIT_EXCEEDED}</li>
 *         <li>{@code "runtime exception"} or {@code "throw new"} →
 *             {@code RUNTIME_ERROR}</li>
 *         <li>{@code "memory"} (with an obvious OOM shape) →
 *             {@code MEMORY_LIMIT_EXCEEDED} (heuristic; not
 *             perfect)</li>
 *         <li>{@code "syntax error"} or {@code "compile error"} →
 *             {@code COMPILE_ERROR}</li>
 *         <li>else → {@code ACCEPTED} (the default for tests)</li>
 *       </ul></li>
 *   <li>Unrecognized language id (no profile registered) →
 *       {@code SANDBOX_ERROR} with detail naming the missing id,
 *       matching the production {@code SandboxExecutorImpl} contract.</li>
 * </ol>
 *
 * <h2>Activation</h2>
 * Activates only when {@code sandbox.executor=inmemory}; mutually
 * exclusive with the production {@code SandboxExecutorImpl} (Spring
 * will fail fast on ambiguous beans if both match — keep the
 * property at {@code docker} in any deployment that runs real
 * submissions).
 *
 * <h2>What this adapter does NOT do</h2>
 * <ul>
 *   <li>Touch the filesystem (no workspace materialization).</li>
 *   <li>Call docker (so tests do not require a daemon).</li>
 *   <li>Read test case inputs — the test case is accepted but not
 *       inspected. The {@code output} / {@code expectedOutput}
 *       fields of the wire DTO are filled in by the upstream facade,
 *       not by the sandbox.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sandbox.executor",
                       havingValue = "inmemory",
                       matchIfMissing = false)
public class InMemorySandboxAdapter implements SandboxExecutor {

    // Pattern tolerates "// verdict: NAME" and "# verdict: NAME" so
    // both Java and Python style comments are recognized in tests.
    private static final Pattern VERDICT_MARKER =
            Pattern.compile("(?:^|\\s)(?://|#)\\s*verdict\\s*:\\s*([A-Z_]+)",
                    Pattern.CASE_INSENSITIVE);

    private final JudgingLanguageSupport languageSupport;

    @Override
    public RunCaseResult run(SandboxJob job, TestCase testCase) {
        return route(job, testCase);
    }

    @Override
    public BatchRunResult runBatch(SandboxJob job, List<TestCase> cases) {
        List<RunCaseResult> out = new ArrayList<>(cases.size());
        for (TestCase tc : cases) {
            out.add(route(job, tc));
        }
        return new BatchRunResult(out);
    }

    private RunCaseResult route(SandboxJob job, TestCase tc) {
        // If the language id is empty, mirror the production
        // behavior: surface as SANDBOX_ERROR with a structured
        // detail so the caller can react.
        if (job.languageId() == null || job.languageId().isBlank()) {
            return RunCaseResult.rejected(SubmissionStatus.SANDBOX_ERROR,
                    "InMemorySandboxAdapter: missing languageId", 0L, 0L);
        }
        // M2a-round-2 fix (codex review F6): an unknown language id
        // (e.g. "ruby") must surface as SANDBOX_ERROR the same way
        // the production SandboxExecutorImpl does when no
        // LanguageProfile is registered. Previously the in-memory
        // adapter fell through to the heuristic and returned
        // ACCEPTED, which let unit tests pass for requests that
        // production would correctly reject.
        //
        // We mirror the production language catalog via
        // JudgingLanguageSupport — the set of ids the production
        // executor has a (potentially-disabled) LanguageProfile for.
        // The 3 stub profiles (JavaScript / C / C++) are excluded by
        // default but still present at the languageId level, so the
        // known-set check happens here before the routing layer.
        // Architecture-review candidate #1: cross this seam rather
        // than importing the submission-internal CodeExecutionHelper.
        if (!languageSupport.isAdvertised(job.languageId())) {
            return RunCaseResult.rejected(SubmissionStatus.SANDBOX_ERROR,
                    "InMemorySandboxAdapter: D-form harness not implemented for language: "
                            + job.languageId(),
                    0L, 0L);
        }

        Optional<RunCaseResult> explicit = explicitVerdict(job);
        if (explicit.isPresent()) {
            return explicit.get();
        }
        return heuristicVerdict(job);
    }

    private Optional<RunCaseResult> explicitVerdict(SandboxJob job) {
        if (job.code() == null) {
            return Optional.empty();
        }
        Matcher m = VERDICT_MARKER.matcher(job.code());
        if (!m.find()) {
            return Optional.empty();
        }
        String name = m.group(1).toUpperCase(Locale.ROOT);
        try {
            SubmissionStatus status = SubmissionStatus.valueOf(name);
            return Optional.of(status == SubmissionStatus.ACCEPTED
                    ? RunCaseResult.accepted(0L, 0L)
                    : RunCaseResult.rejected(status, "explicit verdict: " + name, 0L, 0L));
        } catch (IllegalArgumentException e) {
            // Unknown status name in the marker → fall through to
            // heuristic. Don't fail the test, just ignore the bogus
            // marker.
            return Optional.empty();
        }
    }

    private RunCaseResult heuristicVerdict(SandboxJob job) {
        String code = job.code() == null ? "" : job.code().toLowerCase(Locale.ROOT);
        if (code.contains("infinite loop") || code.contains("while(true)")
                || code.contains("while true") || code.contains("for(;;)")) {
            return RunCaseResult.rejected(SubmissionStatus.TIME_LIMIT_EXCEEDED,
                    "InMemory: heuristic TLE", 0L, 0L);
        }
        if (code.contains("runtime exception") || code.contains("throw new")
                || code.contains("raise ")) {
            return RunCaseResult.rejected(SubmissionStatus.RUNTIME_ERROR,
                    "InMemory: heuristic RE", 0L, 0L);
        }
        if (code.contains("memory") && (code.contains("oom")
                || code.contains("outofmemory") || code.contains("allocate huge"))) {
            return RunCaseResult.rejected(SubmissionStatus.MEMORY_LIMIT_EXCEEDED,
                    "InMemory: heuristic MLE", 0L, 0L);
        }
        if (code.contains("syntax error") || code.contains("compile error")) {
            return RunCaseResult.rejected(SubmissionStatus.COMPILE_ERROR,
                    "InMemory: heuristic CE", 0L, 0L);
        }
        return RunCaseResult.accepted(0L, 0L);
    }
}
