package com.ulticode.app.api.dto;

import java.time.Instant;
import java.io.Serializable;

/**
 * Result returned by
 * {@link com.ulticode.app.api.service.SubmissionAdministrationService#rejudge}.
 *
 * <p>Carries the submission id, the post-rejudge status, the timestamp of
 * the rejudge operation (epoch-millis for serialization safety), and the
 * submission's retry count so the Admin BFF can surface the outcome
 * without an extra RPC.
 *
 * <p>Timestamps use {@code long epochMillis} (not {@link Instant} or
 * {@code LocalDateTime}) to keep the Dubbo Triple wire shape stable
 * across JDK versions without requiring jackson-datatype-jsr310.
 */
public record RejudgeResultDTO(
        String submissionId,
        String newStatus,
        long rejudgedAtEpochMs,
        int retryCount) implements Serializable {
}
