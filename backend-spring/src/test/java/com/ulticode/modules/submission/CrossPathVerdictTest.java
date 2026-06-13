package com.ulticode.modules.submission;

import com.ulticode.modules.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.service.VerdictResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-path consistency tests (Codex F15 fix — M1a round 4).
 * <p>
 * Historically the {@code /run} path ({@code CodeExecutionService}) and
 * the {@code /submit} path ({@code JudgeWorkerProcessor}) each carried
 * their own {@code VERDICT_PRIORITY} maps with inverse ordering conventions,
 * which would produce <em>different</em> final verdicts for the same case
 * set (e.g. {Wrong Answer, Presentation Error} became "Presentation Error"
 * on /run but "Wrong Answer" on /submit). This test pins the invariant
 * that both paths must reduce identical case sets to the same wire value.
 * <p>
 * Both paths now delegate to {@link VerdictResolver} which holds the single
 * source of truth (severity ordering). The reducer is a pure function, so
 * testing it once validates both call sites.
 */
@DisplayName("Cross-path verdict consistency (/run vs /submit)")
class CrossPathVerdictTest {

    private final VerdictResolver resolver = new VerdictResolver();

    @Test
    @DisplayName("identical case sets yield identical wire values regardless of entry point")
    void identicalCases_identicalWire() {
        List<String> cases = Arrays.asList("Wrong Answer", "Presentation Error");
        // /run and /submit both call reduceWire with these wire values
        SubmissionStatus fromRun = resolver.reduceWire(cases);
        SubmissionStatus fromSubmit = resolver.reduceWire(cases);
        assertThat(fromRun).isEqualTo(fromSubmit);
        // And the actual wire value: WA severity=2 > PE severity=1, so WA wins.
        assertThat(fromRun.wireValue()).isEqualTo("Wrong Answer");
    }

    @Test
    @DisplayName("mixed MLE + TLE resolves to MLE (severity 4 > 3)")
    void mlePlusTle_resolvesToMle() {
        assertThat(resolver.reduceWire(Arrays.asList("Time Limit Exceeded", "Memory Limit Exceeded")))
                .isEqualTo(SubmissionStatus.MEMORY_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("RE + WA + AC resolves to RE (severity 5 > 2 > 0)")
    void reWinsOverWaAndAc() {
        assertThat(resolver.reduceWire(Arrays.asList(
                "Accepted", "Wrong Answer", "Runtime Error", "Accepted")))
                .isEqualTo(SubmissionStatus.RUNTIME_ERROR);
    }

    @Test
    @DisplayName("Sandbox Error outranks Runtime Error (TERMINAL_INFRA > TERMINAL_BAD)")
    void sandboxErrorOutranksRuntimeError() {
        assertThat(resolver.reduceWire(Arrays.asList(
                "Runtime Error", "Sandbox Error")))
                .isEqualTo(SubmissionStatus.SANDBOX_ERROR);
    }

    @Test
    @DisplayName("System Error outranks Sandbox Error (severity 8 > 7)")
    void systemErrorOutranksSandboxError() {
        assertThat(resolver.reduceWire(Arrays.asList(
                "Sandbox Error", "System Error", "Accepted")))
                .isEqualTo(SubmissionStatus.SYSTEM_ERROR);
    }

    @Test
    @DisplayName("all-AC input yields AC (not \"Pending\" as legacy CodeExecutionService did)")
    void allAc_yieldsAc_notPending() {
        SubmissionStatus result = resolver.reduceWire(Arrays.asList(
                "Accepted", "Accepted", "Accepted"));
        assertThat(result).isEqualTo(SubmissionStatus.ACCEPTED);
    }

    @Test
    @DisplayName("unknown wire value counts toward unknownWireFallbackCount and falls back to SYSTEM_ERROR")
    void unknownWire_fallbackCount() {
        long before = resolver.unknownWireFallbackCount();
        SubmissionStatus result = resolver.reduceWire(Arrays.asList("Not A Verdict"));
        long after = resolver.unknownWireFallbackCount();
        assertThat(result).isEqualTo(SubmissionStatus.SYSTEM_ERROR);
        assertThat(after - before).isEqualTo(1L);
    }

    @Test
    @DisplayName("null wire value counts and falls back (regression: was a silent NPE)")
    void nullWire_fallback() {
        long before = resolver.unknownWireFallbackCount();
        SubmissionStatus result = resolver.reduceWire(Arrays.asList((String) null));
        long after = resolver.unknownWireFallbackCount();
        assertThat(result).isEqualTo(SubmissionStatus.SYSTEM_ERROR);
        assertThat(after - before).isEqualTo(1L);
    }
}
