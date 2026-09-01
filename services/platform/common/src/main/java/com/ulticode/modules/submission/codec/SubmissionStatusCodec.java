package com.ulticode.modules.submission.codec;

import com.ulticode.domain.submission.enums.SubmissionStatus;

/**
 * Codec utility for {@link SubmissionStatus} to wire-string conversion.
 *
 * <p>The wire value is the durable submission-status contract used by the
 * database, JSON payloads, and worker events. Keeping this codec in the
 * dependency-free common module prevents the App and Judge runtimes from
 * depending on one another for status conversion.
 */
public final class SubmissionStatusCodec {

    private SubmissionStatusCodec() {
        // utility class
    }

    /** Decode a wire value strictly; unknown values are contract errors. */
    public static SubmissionStatus fromWire(String wire) {
        return SubmissionStatus.fromWire(wire);
    }

    /** Decode a historic value leniently; unknown/null values become null. */
    public static SubmissionStatus fromWireLenient(String wire) {
        return SubmissionStatus.fromDbName(wire);
    }

    /** Encode an enum value to its durable wire value. */
    public static String toWire(SubmissionStatus status) {
        return status == null ? null : status.wireValue();
    }
}
