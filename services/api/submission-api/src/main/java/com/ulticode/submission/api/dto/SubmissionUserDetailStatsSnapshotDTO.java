package com.ulticode.submission.api.dto;

import java.io.Serializable;

/**
 * Submission-owned statistics needed to render one Admin user detail.
 *
 * <p>The snapshot deliberately contains only values owned by Submission. A
 * successful snapshot may contain real zeroes; provider failure is represented
 * by the surrounding {@code RpcResult} rather than by zero or {@code null}
 * values.
 */
public record SubmissionUserDetailStatsSnapshotDTO(
        long submissionCount,
        long acceptedProblemCount,
        int streak
) implements Serializable {
    private static final long serialVersionUID = 1L;

    public SubmissionUserDetailStatsSnapshotDTO {
        if (submissionCount < 0) {
            throw new IllegalArgumentException("submissionCount must not be negative");
        }
        if (acceptedProblemCount < 0) {
            throw new IllegalArgumentException("acceptedProblemCount must not be negative");
        }
        if (streak < 0) {
            throw new IllegalArgumentException("streak must not be negative");
        }
    }
}
