package com.ulticode.modules.submission.service;

import com.ulticode.domain.submission.enums.SubmissionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link VerdictResolver}.
 * Covers BCDE: borders (empty / single / all-AC), correct multi-case reduction,
 * design contract (severity ordering), and error paths (null input, in-flight case).
 */
@DisplayName("VerdictResolver")
class VerdictResolverTest {

    private final VerdictResolver resolver = new VerdictResolver();

    // === reduce(enum collection) ===

    @Nested
    @DisplayName("reduce(enum collection)")
    class ReduceEnum {

        @Test
        @DisplayName("empty input returns SYSTEM_ERROR")
        void emptyInput_returnsSystemError() {
            assertThat(resolver.reduce(Collections.emptyList()))
                    .isEqualTo(SubmissionStatus.SYSTEM_ERROR);
        }

        @Test
        @DisplayName("null input throws IllegalArgumentException")
        void nullInput_throws() {
            assertThatThrownBy(() -> resolver.reduce(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("single ACCEPTED case returns ACCEPTED")
        void singleAccepted_returnsAccepted() {
            assertThat(resolver.reduce(List.of(SubmissionStatus.ACCEPTED)))
                    .isEqualTo(SubmissionStatus.ACCEPTED);
        }

        @Test
        @DisplayName("all ACCEPTED cases return ACCEPTED")
        void allAccepted_returnsAccepted() {
            assertThat(resolver.reduce(Arrays.asList(
                    SubmissionStatus.ACCEPTED,
                    SubmissionStatus.ACCEPTED,
                    SubmissionStatus.ACCEPTED)))
                    .isEqualTo(SubmissionStatus.ACCEPTED);
        }

        @Test
        @DisplayName("worst-severity case wins (RE > WA > AC)")
        void worstSeverityWins() {
            assertThat(resolver.reduce(Arrays.asList(
                    SubmissionStatus.ACCEPTED,
                    SubmissionStatus.WRONG_ANSWER,
                    SubmissionStatus.RUNTIME_ERROR,
                    SubmissionStatus.ACCEPTED)))
                    .isEqualTo(SubmissionStatus.RUNTIME_ERROR);
        }

        @Test
        @DisplayName("TERMINAL_INFRA (SANDBOX_ERROR) outranks TERMINAL_BAD (RE)")
        void infraOutranksBad() {
            assertThat(resolver.reduce(Arrays.asList(
                    SubmissionStatus.RUNTIME_ERROR,
                    SubmissionStatus.SANDBOX_ERROR)))
                    .isEqualTo(SubmissionStatus.SANDBOX_ERROR);
        }

        @Test
        @DisplayName("SYSTEM_ERROR outranks SANDBOX_ERROR (severity 8 > 7)")
        void systemOutranksSandbox() {
            assertThat(resolver.reduce(Arrays.asList(
                    SubmissionStatus.SANDBOX_ERROR,
                    SubmissionStatus.SYSTEM_ERROR)))
                    .isEqualTo(SubmissionStatus.SYSTEM_ERROR);
        }

        @Test
        @DisplayName("MLE and OLE are tied at severity 4; first-encountered wins (stable)")
        void tiedSeverity_keepsExistingWinner() {
            // The reducer uses strict-greater-than, so the first one encountered stays.
            assertThat(resolver.reduce(Arrays.asList(
                    SubmissionStatus.MEMORY_LIMIT_EXCEEDED,
                    SubmissionStatus.OUTPUT_LIMIT_EXCEEDED)))
                    .isEqualTo(SubmissionStatus.MEMORY_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("null elements are skipped")
        void nullElements_skipped() {
            assertThat(resolver.reduce(Arrays.asList(
                    null,
                    SubmissionStatus.WRONG_ANSWER,
                    null)))
                    .isEqualTo(SubmissionStatus.WRONG_ANSWER);
        }

        @Test
        @DisplayName("in-flight case (PENDING) triggers IllegalStateException")
        void pendingCase_throws() {
            assertThatThrownBy(() -> resolver.reduce(Arrays.asList(
                    SubmissionStatus.ACCEPTED,
                    SubmissionStatus.PENDING)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("in-flight");
        }

        @Test
        @DisplayName("in-flight case (JUDGING) triggers IllegalStateException")
        void judgingCase_throws() {
            assertThatThrownBy(() -> resolver.reduce(List.of(SubmissionStatus.JUDGING)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // === reduceWire(String collection) ===

    @Nested
    @DisplayName("reduceWire(String collection)")
    class ReduceWire {

        @Test
        @DisplayName("matches enum reduce for canonical wire values")
        void canonicalWires_matchEnumReduce() {
            assertThat(resolver.reduceWire(Arrays.asList(
                    "Accepted", "Wrong Answer", "Runtime Error")))
                    .isEqualTo(SubmissionStatus.RUNTIME_ERROR);
        }

        @Test
        @DisplayName("unknown wire string treated as SYSTEM_ERROR (severity 8)")
        void unknownWire_treatedAsSystemError() {
            assertThat(resolver.reduceWire(Arrays.asList(
                    "Accepted", "Some Garbage Value")))
                    .isEqualTo(SubmissionStatus.SYSTEM_ERROR);
        }

        @Test
        @DisplayName("empty input returns SYSTEM_ERROR")
        void emptyWireInput_returnsSystemError() {
            assertThat(resolver.reduceWire(Collections.emptyList()))
                    .isEqualTo(SubmissionStatus.SYSTEM_ERROR);
        }

        @Test
        @DisplayName("null wire input throws IllegalArgumentException")
        void nullWireInput_throws() {
            assertThatThrownBy(() -> resolver.reduceWire(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("wire value 'Sandbox Error' decoded correctly (new in M1a)")
        void sandboxErrorWire_decoded() {
            assertThat(resolver.reduceWire(List.of("Sandbox Error")))
                    .isEqualTo(SubmissionStatus.SANDBOX_ERROR);
        }

        @Test
        @DisplayName("wire value 'Output Limit Exceeded' decoded correctly")
        void outputLimitExceededWire_decoded() {
            assertThat(resolver.reduceWire(List.of("Output Limit Exceeded")))
                    .isEqualTo(SubmissionStatus.OUTPUT_LIMIT_EXCEEDED);
        }
    }
}
