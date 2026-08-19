package com.ulticode.submission.api.dto;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable facts captured by the request owner before Submission intake.
 *
 * <p>The snapshot contains no entity, mapper, repository, credential, or
 * implementation type. Submission uses it only to validate that the request
 * was admitted against the problem and identity facts observed by App/Auth.
 */
public record SubmissionFactsSnapshot(
        String userId,
        boolean userExists,
        ProblemFacts problem,
        long capturedAtEpochMillis,
        int schemaVersion
) implements Serializable {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public SubmissionFactsSnapshot {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        if (capturedAtEpochMillis < 0) {
            throw new IllegalArgumentException("capturedAtEpochMillis must not be negative");
        }
    }

    /**
     * Returns whether this snapshot admits the supplied command identity and
     * problem. The owner must still enforce command-shape and language rules.
     */
    public boolean admits(String requestedUserId, Long requestedProblemId) {
        return schemaVersion == CURRENT_SCHEMA_VERSION
                && userExists
                && Objects.equals(userId, requestedUserId)
                && problem != null
                && Objects.equals(problem.id(), requestedProblemId);
    }

    public record ProblemFacts(
            Long id,
            String title,
            String slug,
            Integer timeLimitSeconds,
            Integer memoryLimitMb,
            String starterCode
    ) implements Serializable {
    }
}
