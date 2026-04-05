package com.ulticode.modules.submission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Submission status metadata for frontend display.
 */
@Data
@Schema(description = "Submission status metadata")
public class SubmissionStatusMeta {

    @Schema(description = "Status key")
    private String key;

    @Schema(description = "Status code")
    private String code;

    @Schema(description = "Display label")
    private String label;

    @Schema(description = "Status description")
    private String description;

    @Schema(description = "Suggestion for fixing this status")
    private String suggestion;

    @Schema(description = "Status category (success, error, warning, pending, system)")
    private String category;

    @Schema(description = "Severity level (success, error, warning, info)")
    private String severity;

    @Schema(description = "Whether this status is terminal (no more processing)")
    private Boolean isTerminal;

    @Schema(description = "Sort order for display")
    private Integer sortOrder;
}
