package com.ulticode.submission.api.dto;

import java.io.Serializable;

/** Minimal Submission-owner facts used by App contest finalization. */
public record SubmissionAdjudicationFact(
        String submissionId,
        Long generation,
        String status
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
