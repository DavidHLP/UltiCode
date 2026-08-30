package com.ulticode.submission.api.dto;

import java.io.Serializable;

/** Bounded count of Submission rows grouped by referenced account id. */
public record SubmissionUserReferenceCountDTO(
        String accountId,
        long rowCount) implements Serializable {
    private static final long serialVersionUID = 1L;
}
