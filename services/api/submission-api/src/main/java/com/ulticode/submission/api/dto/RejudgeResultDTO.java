package com.ulticode.submission.api.dto;

import java.time.Instant;
import java.io.Serializable;

/**
 * Result returned by
 * {@link com.ulticode.submission.api.service.SubmissionAdministrationService#rejudge}.
 *
 * <p>The nullable {@code success} component is deliberate: during a rolling
 * deployment an older provider may omit the field. Consumers treat a missing
 * value as the legacy successful result while explicit {@code false} remains
 * a per-submission failure.
 *
 * <p>Timestamps use {@code long epochMillis} (not {@link Instant} or
 * {@code LocalDateTime}) to keep the Dubbo Triple wire shape stable
 * across JDK versions without requiring jackson-datatype-jsr310.
 */
public record RejudgeResultDTO(
        String submissionId,
        String newStatus,
        long rejudgedAtEpochMs,
        int retryCount,
        Boolean success,
        Integer errorCode,
        String error) implements Serializable {
    private static final long serialVersionUID = 1L;


    /**
     * Backward-compatible successful result constructor used by existing
     * contract fixtures.
     */
    public RejudgeResultDTO(
            String submissionId, String newStatus,
            long rejudgedAtEpochMs, int retryCount) {
        this(submissionId, newStatus, rejudgedAtEpochMs, retryCount,
                true, null, null);
    }
}
