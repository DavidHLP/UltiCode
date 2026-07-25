package com.ulticode.modules.submission.codec;

import com.ulticode.modules.submission.enums.SubmissionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SubmissionStatusCodec} (ADR-001 wire contract).
 * Covers BCDE: borders (null in/out), correct round-trip for every enum
 * constant, design contract (wireValue is the durable string), and error
 * paths (unknown wire value, strict vs lenient decoding).
 */
@DisplayName("SubmissionStatusCodec")
class SubmissionStatusCodecTest {

    // === fromWire (strict) ===

    @Test
    @DisplayName("fromWire decodes every enum constant by its wire value")
    void fromWire_roundTripsAllConstants() {
        for (SubmissionStatus s : SubmissionStatus.values()) {
            assertThat(SubmissionStatusCodec.fromWire(s.wireValue()))
                    .as("round-trip for %s", s.name())
                    .isEqualTo(s);
        }
    }

    @Test
    @DisplayName("fromWire throws on unknown wire value (fail-fast)")
    void fromWire_unknown_throws() {
        assertThatThrownBy(() -> SubmissionStatusCodec.fromWire("Not A Verdict"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not A Verdict");
    }

    @Test
    @DisplayName("fromWire throws on null")
    void fromWire_null_throws() {
        assertThatThrownBy(() -> SubmissionStatusCodec.fromWire(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("fromWire is case-sensitive (wire contract is exact)")
    void fromWire_caseSensitive() {
        assertThatThrownBy(() -> SubmissionStatusCodec.fromWire("accepted"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // === fromWireLenient ===

    @Test
    @DisplayName("fromWireLenient returns null for unknown values")
    void fromWireLenient_unknown_returnsNull() {
        assertThat(SubmissionStatusCodec.fromWireLenient("Legacy Value"))
                .isNull();
    }

    @Test
    @DisplayName("fromWireLenient returns null for null input")
    void fromWireLenient_null_returnsNull() {
        assertThat(SubmissionStatusCodec.fromWireLenient(null))
                .isNull();
    }

    @Test
    @DisplayName("fromWireLenient agrees with fromWire on known values")
    void fromWireLenient_matchesStrictOnKnown() {
        for (SubmissionStatus s : SubmissionStatus.values()) {
            assertThat(SubmissionStatusCodec.fromWireLenient(s.wireValue()))
                    .as("lenient round-trip for %s", s.name())
                    .isEqualTo(s);
        }
    }

    // === toWire ===

    @Test
    @DisplayName("toWire returns the displayName for every constant")
    void toWire_returnsWireValue() {
        for (SubmissionStatus s : SubmissionStatus.values()) {
            assertThat(SubmissionStatusCodec.toWire(s)).isEqualTo(s.getDisplayName());
        }
    }

    @Test
    @DisplayName("toWire returns null for null input")
    void toWire_null_returnsNull() {
        assertThat(SubmissionStatusCodec.toWire(null)).isNull();
    }

    // === wire-string stability (regression guard) ===

    @Test
    @DisplayName("wire strings are the documented exact values (regression guard)")
    void wireStrings_areExpectedConstants() {
        // Locking these prevents accidental rename / case changes that would
        // break the wire contract (ADR-001 invariant).
        assertThat(SubmissionStatus.PENDING.wireValue()).isEqualTo("Pending");
        assertThat(SubmissionStatus.JUDGING.wireValue()).isEqualTo("Judging");
        assertThat(SubmissionStatus.ACCEPTED.wireValue()).isEqualTo("Accepted");
        assertThat(SubmissionStatus.PRESENTATION_ERROR.wireValue()).isEqualTo("Presentation Error");
        assertThat(SubmissionStatus.WRONG_ANSWER.wireValue()).isEqualTo("Wrong Answer");
        assertThat(SubmissionStatus.TIME_LIMIT_EXCEEDED.wireValue()).isEqualTo("Time Limit Exceeded");
        assertThat(SubmissionStatus.MEMORY_LIMIT_EXCEEDED.wireValue())
                .isEqualTo("Memory Limit Exceeded");
        assertThat(SubmissionStatus.OUTPUT_LIMIT_EXCEEDED.wireValue())
                .isEqualTo("Output Limit Exceeded");
        assertThat(SubmissionStatus.RUNTIME_ERROR.wireValue()).isEqualTo("Runtime Error");
        assertThat(SubmissionStatus.COMPILE_ERROR.wireValue()).isEqualTo("Compile Error");
        assertThat(SubmissionStatus.SANDBOX_ERROR.wireValue()).isEqualTo("Sandbox Error");
        assertThat(SubmissionStatus.SYSTEM_ERROR.wireValue()).isEqualTo("System Error");
    }

    @Test
    @DisplayName("all wire values are unique (no two enums share displayName)")
    void wireStrings_areUnique() {
        long uniqueCount = java.util.Arrays.stream(SubmissionStatus.values())
                .map(SubmissionStatus::wireValue)
                .distinct()
                .count();
        assertThat(uniqueCount).isEqualTo(SubmissionStatus.values().length);
    }

    @Test
    @DisplayName("severity ordering — terminal infra > compile > RE > MLE/OLE > TLE > WA > PE > AC")
    void severityOrdering_isMonotonic() {
        assertThat(SubmissionStatus.ACCEPTED.getSeverity()).isEqualTo(0);
        assertThat(SubmissionStatus.PRESENTATION_ERROR.getSeverity())
                .isGreaterThan(SubmissionStatus.ACCEPTED.getSeverity());
        assertThat(SubmissionStatus.WRONG_ANSWER.getSeverity())
                .isGreaterThan(SubmissionStatus.PRESENTATION_ERROR.getSeverity());
        assertThat(SubmissionStatus.TIME_LIMIT_EXCEEDED.getSeverity())
                .isGreaterThan(SubmissionStatus.WRONG_ANSWER.getSeverity());
        assertThat(SubmissionStatus.MEMORY_LIMIT_EXCEEDED.getSeverity())
                .isGreaterThan(SubmissionStatus.TIME_LIMIT_EXCEEDED.getSeverity());
        assertThat(SubmissionStatus.RUNTIME_ERROR.getSeverity())
                .isGreaterThan(SubmissionStatus.MEMORY_LIMIT_EXCEEDED.getSeverity());
        assertThat(SubmissionStatus.COMPILE_ERROR.getSeverity())
                .isGreaterThan(SubmissionStatus.RUNTIME_ERROR.getSeverity());
        assertThat(SubmissionStatus.SANDBOX_ERROR.getSeverity())
                .isGreaterThan(SubmissionStatus.COMPILE_ERROR.getSeverity());
        assertThat(SubmissionStatus.SYSTEM_ERROR.getSeverity())
                .isGreaterThan(SubmissionStatus.SANDBOX_ERROR.getSeverity());
    }
}
