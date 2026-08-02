package com.ulticode.modules.submission.codec;

import com.ulticode.domain.submission.enums.SubmissionStatus;

/**
 * Codec utility for {@link SubmissionStatus} <-> wire-string conversion (ADR-001).
 * <p>
 * The wire string is the value stored in {@code submissions.status} and shipped
 * over JSON (e.g. {@code "Wrong Answer"}, {@code "Compile Error"}). It is the
 * durable contract; the enum constant name ({@link SubmissionStatus#name()})
 * is <b>not</b> a wire contract.
 * <p>
 * This codec exists so that any code dealing with wire values (MyBatis row
 * mapping, queue payloads, controller responses constructed by hand) goes
 * through a single typed boundary instead of comparing strings ad hoc.
 * <p>
 * Jackson serialization/deserialization already goes through
 * {@code @JsonValue} / {@code @JsonCreator} on the enum itself; this codec
 * is for non-Jackson sites.
 */
public final class SubmissionStatusCodec {

    private SubmissionStatusCodec() {
        // utility class
    }

    /**
     * Decode a wire string to its enum constant. Strict — throws on unknown values.
     * Use this in new code where unknown wire values indicate a contract bug.
     *
     * @param wire the wire string; must not be {@code null}
     * @return the matching enum constant
     * @throws IllegalArgumentException if {@code wire} is {@code null} or unknown
     */
    public static SubmissionStatus fromWire(String wire) {
        return SubmissionStatus.fromWire(wire);
    }

    /**
     * Lenient decode — returns {@code null} for unknown values instead of throwing.
     * Use this when reading historic DB rows that may contain values from older
     * deployments. New code should prefer {@link #fromWire(String)}.
     *
     * @param wire the wire string; may be {@code null}
     * @return the matching enum constant, or {@code null} if {@code wire} is
     *         {@code null} or unknown
     */
    public static SubmissionStatus fromWireLenient(String wire) {
        return SubmissionStatus.fromDbName(wire);
    }

    /**
     * Encode an enum constant to its wire string. {@code null} in, {@code null} out.
     *
     * @param status the enum constant; may be {@code null}
     * @return the wire string, or {@code null} if {@code status} is {@code null}
     */
    public static String toWire(SubmissionStatus status) {
        return status == null ? null : status.wireValue();
    }
}
