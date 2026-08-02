package com.ulticode.app.api.dto;

import lombok.Data;

import java.time.Instant;

/**
 * Result of a rejudge operation.
 * Used by the {@link com.ulticode.app.api.service.RejudgePolicy} port.
 */
@Data
public class RejudgeResult {

    private String submissionId;

    private Boolean success;

    private String oldStatus;

    private String newStatus;

    private String error;

    private Instant rejudgedAt;

    private Integer retryCount;
}
