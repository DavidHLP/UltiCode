package com.ulticode.modules.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Result of a rejudge operation.
 */
@Data
@Schema(description = "Rejudge operation result")
public class RejudgeResult {

    @Schema(description = "Submission ID that was rejudged")
    private String submissionId;

    @Schema(description = "Whether the rejudge was successfully initiated")
    private Boolean success;

    @Schema(description = "Old status before rejudge")
    private String oldStatus;

    @Schema(description = "New status after rejudge (if available)")
    private String newStatus;

    @Schema(description = "Error message if rejudge failed")
    private String error;
}
