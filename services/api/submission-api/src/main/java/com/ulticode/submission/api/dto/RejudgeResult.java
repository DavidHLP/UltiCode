package com.ulticode.submission.api.dto;

import lombok.Data;
import java.io.Serializable;

import java.time.Instant;

/**
 * Result of a rejudge operation.
 * Used by the {@link com.ulticode.submission.api.service.RejudgePolicy} port.
 */
@Data
public class RejudgeResult implements Serializable {
    private static final long serialVersionUID = 1L;


    private String submissionId;

    private Boolean success;

    private String oldStatus;

    private String newStatus;

    private String error;

    /** Stable App error code when {@link #success} is {@code false}. */
    private Integer errorCode;

    private Instant rejudgedAt;

    private Integer retryCount;
}
