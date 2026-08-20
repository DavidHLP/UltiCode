package com.ulticode.submission.api.dto;

import java.io.Serializable;

/** Entity-free Submission-owner aggregates used by the Admin Dashboard. */
public record SubmissionDashboardStatsDTO(
        long total,
        long today,
        long week,
        long month,
        double acceptanceRate) implements Serializable {
    private static final long serialVersionUID = 1L;
}
