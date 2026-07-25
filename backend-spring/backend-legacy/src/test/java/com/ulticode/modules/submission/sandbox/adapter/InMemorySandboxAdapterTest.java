package com.ulticode.modules.submission.sandbox.adapter;

import com.ulticode.modules.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.port.DefaultJudgingLanguageSupport;
import com.ulticode.modules.submission.port.JudgingLanguageSupport;
import com.ulticode.modules.submission.sandbox.RunCaseResult;
import com.ulticode.modules.submission.sandbox.SandboxJob;
import com.ulticode.modules.submission.sandbox.TestCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verdict routing for the in-memory sandbox adapter (ADR-002 §2.3).
 *
 * <p>The adapter is the primary off-daemon test fixture for the
 * sandbox port, so a regression here propagates to every unit test
 * that exercises the executor via {@code sandbox.executor=inmemory}.
 */
@DisplayName("InMemorySandboxAdapter (ADR-002 §2.3)")
class InMemorySandboxAdapterTest {

    private final JudgingLanguageSupport languageSupport = new DefaultJudgingLanguageSupport();
    private final InMemorySandboxAdapter adapter = new InMemorySandboxAdapter(languageSupport);

    private static SandboxJob job(String language, String code) {
        return new SandboxJob(
                "run-1", "user-1", "sub-1", 0L,
                language, code, 2, 256);
    }

    private static TestCase singleCase() {
        return new TestCase("tc-1", "Example 1", List.of(), "42");
    }

    @Nested
    @DisplayName("run()")
    class Run {

        @Test
        @DisplayName("ACCEPTED explicit marker routes to ACCEPTED with score 1.0")
        void run_explicitAccepted_returnsAccepted() {
            RunCaseResult r = adapter.run(
                    job("java", "return 0; // verdict: ACCEPTED"),
                    singleCase());
            assertThat(r.status()).isEqualTo(SubmissionStatus.ACCEPTED);
            assertThat(r.score()).isEqualTo(1.0);
        }

        @ParameterizedTest
        @CsvSource({
                "RUNTIME_ERROR, RUNTIME_ERROR",
                "WRONG_ANSWER, WRONG_ANSWER",
                "TIME_LIMIT_EXCEEDED, TIME_LIMIT_EXCEEDED",
                "COMPILE_ERROR, COMPILE_ERROR",
                "MEMORY_LIMIT_EXCEEDED, MEMORY_LIMIT_EXCEEDED",
                "SANDBOX_ERROR, SANDBOX_ERROR"
        })
        @DisplayName("explicit verdict markers map through SubmissionStatus enum")
        void run_explicitMarker_mapsToEnum(String marker, String expected) {
            String code = "throw new RuntimeException(); // verdict: " + marker;
            RunCaseResult r = adapter.run(job("java", code), singleCase());
            assertThat(r.status().name()).isEqualTo(expected);
            assertThat(r.score()).isEqualTo(0.0);
            assertThat(r.detail()).contains(marker);
        }

        @Test
        @DisplayName("Python-style # verdict: marker is also recognized")
        void run_pythonStyleMarker_recognized() {
            String code = "raise Exception()  # verdict: RUNTIME_ERROR";
            RunCaseResult r = adapter.run(job("python", code), singleCase());
            assertThat(r.status()).isEqualTo(SubmissionStatus.RUNTIME_ERROR);
        }

        @Test
        @DisplayName("infinite-loop heuristic maps to TIME_LIMIT_EXCEEDED")
        void run_heuristicTle_detected() {
            RunCaseResult r = adapter.run(
                    job("java", "while(true) { /* infinite loop */ }"),
                    singleCase());
            assertThat(r.status()).isEqualTo(SubmissionStatus.TIME_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("throw new heuristic maps to RUNTIME_ERROR")
        void run_heuristicRuntimeError_detected() {
            RunCaseResult r = adapter.run(
                    job("java", "throw new IllegalStateException();"),
                    singleCase());
            assertThat(r.status()).isEqualTo(SubmissionStatus.RUNTIME_ERROR);
        }

        @Test
        @DisplayName("Python raise heuristic maps to RUNTIME_ERROR")
        void run_pythonRaiseHeuristic_detected() {
            RunCaseResult r = adapter.run(
                    job("python", "raise ValueError()"),
                    singleCase());
            assertThat(r.status()).isEqualTo(SubmissionStatus.RUNTIME_ERROR);
        }

        @Test
        @DisplayName("oom heuristic maps to MEMORY_LIMIT_EXCEEDED")
        void run_oomHeuristic_detected() {
            RunCaseResult r = adapter.run(
                    job("java", "// allocate huge memory to trigger oom\nbyte[] x = new byte[huge];"),
                    singleCase());
            assertThat(r.status()).isEqualTo(SubmissionStatus.MEMORY_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("compile-error heuristic maps to COMPILE_ERROR")
        void run_compileErrorHeuristic_detected() {
            RunCaseResult r = adapter.run(
                    job("java", "this is a syntax error"),
                    singleCase());
            assertThat(r.status()).isEqualTo(SubmissionStatus.COMPILE_ERROR);
        }

        @Test
        @DisplayName("default code without markers routes to ACCEPTED")
        void run_default_returnsAccepted() {
            RunCaseResult r = adapter.run(
                    job("java", "return 0;"),
                    singleCase());
            assertThat(r.status()).isEqualTo(SubmissionStatus.ACCEPTED);
        }

        @Test
        @DisplayName("null code is treated like a no-op (ACCEPTED)")
        void run_nullCode_returnsAccepted() {
            RunCaseResult r = adapter.run(
                    job("java", null),
                    singleCase());
            assertThat(r.status()).isEqualTo(SubmissionStatus.ACCEPTED);
        }

        @Test
        @DisplayName("missing languageId returns SANDBOX_ERROR with structured detail")
        void run_missingLanguageId_returnsSandboxError() {
            RunCaseResult r = adapter.run(
                    job("", "return 0;"),
                    singleCase());
            assertThat(r.status()).isEqualTo(SubmissionStatus.SANDBOX_ERROR);
            assertThat(r.detail()).contains("missing languageId");
        }

        @Test
        @DisplayName("M2a-round-2 (codex F6): unknown languageId returns SANDBOX_ERROR (matches production)")
        void run_unknownLanguageId_returnsSandboxError() {
            // The production SandboxExecutorImpl returns SANDBOX_ERROR
            // when no LanguageProfile is registered for the job's
            // languageId; before the round-2 fix, the in-memory
            // adapter fell through to the heuristic and silently
            // returned ACCEPTED, letting unit tests pass for
            // requests that production would correctly reject.
            RunCaseResult r = adapter.run(
                    job("ruby", "puts 'hello'"),
                    singleCase());
            assertThat(r.status()).isEqualTo(SubmissionStatus.SANDBOX_ERROR);
            assertThat(r.detail()).contains("D-form harness not implemented");
            assertThat(r.detail()).contains("ruby");
        }

        @Test
        @DisplayName("unknown explicit marker falls through to heuristic, not throws")
        void run_unknownMarker_fallsThroughToHeuristic() {
            // Bogus marker should not crash the adapter — it should
            // just be ignored and the default heuristic applied.
            RunCaseResult r = adapter.run(
                    job("java", "return 0; // verdict: NOT_A_REAL_STATUS"),
                    singleCase());
            assertThat(r.status()).isEqualTo(SubmissionStatus.ACCEPTED);
        }
    }

    @Nested
    @DisplayName("runBatch()")
    class RunBatch {

        @Test
        @DisplayName("preserves input order and length (1:1 contract from ADR-002 §2.5)")
        void runBatch_preservesOrderAndLength() {
            List<TestCase> cases = List.of(
                    new TestCase("tc-1", null, List.of(), "1"),
                    new TestCase("tc-2", null, List.of(), "2"),
                    new TestCase("tc-3", null, List.of(), "3")
            );
            var result = adapter.runBatch(job("java", "return 0;"), cases);
            assertThat(result.cases()).hasSize(3);
            assertThat(result.cases().get(0).status()).isEqualTo(SubmissionStatus.ACCEPTED);
            assertThat(result.cases().get(1).status()).isEqualTo(SubmissionStatus.ACCEPTED);
            assertThat(result.cases().get(2).status()).isEqualTo(SubmissionStatus.ACCEPTED);
        }

        @Test
        @DisplayName("batch routes on job.code — same verdict per case (intentional)")
        void runBatch_mixedVerdicts() {
            List<TestCase> cases = List.of(
                    new TestCase("tc-1", null, List.of(), "ok"),
                    new TestCase("tc-2", null, List.of(), "ok")
            );
            // The adapter routes on job.code() which is per-batch,
            // not per-case, so both cases share the same verdict.
            // This is intentional — the in-memory adapter is a
            // routing fixture, not a per-case verdict engine.
            var ok = adapter.runBatch(job("java", "return 0;"), cases);
            assertThat(ok.cases()).allSatisfy(c ->
                    assertThat(c.status()).isEqualTo(SubmissionStatus.ACCEPTED));

            var re = adapter.runBatch(job("java", "throw new Exception();"), cases);
            assertThat(re.cases()).allSatisfy(c ->
                    assertThat(c.status()).isEqualTo(SubmissionStatus.RUNTIME_ERROR));
        }
    }
}
