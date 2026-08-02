package com.ulticode.app.api.dto;

import lombok.Data;

/**
 * Submission status metadata for frontend display.
 */
@Data
public class SubmissionStatusMeta {

    private String key;

    private String code;

    private String label;

    private String description;

    private String suggestion;

    private String category;

    private String severity;

    private Boolean isTerminal;

    private Integer sortOrder;
}
