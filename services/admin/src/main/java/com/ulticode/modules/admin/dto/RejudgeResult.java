package com.ulticode.modules.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;

/**
 * Result of a rejudge operation.
 *
 * <p>Fields {@link #rejudgedAt} and {@link #retryCount} were added so admin
 * UIs and audit consumers can observe that a rejudge actually happened even
 * when the {@code oldStatus} and {@code newStatus} are identical (e.g. a
 * {@code Pending} submission requeued while already {@code Pending}).</p>
 */
@Data
@Schema(description = "Rejudge operation result")
public class RejudgeResult {

    /** Submission ID that was rejudged. */
    @Schema(description = "Submission ID that was rejudged")
    private String submissionId;

    /** Whether the rejudge was successfully initiated (job enqueued). */
    @Schema(description = "Whether the rejudge was successfully initiated")
    private Boolean success;

    /** Status of the submission before the rejudge was requested. */
    @Schema(description = "Old status before rejudge")
    private String oldStatus;

    /** Status of the submission after the rejudge was requested. */
    @Schema(description = "New status after rejudge (if available)")
    private String newStatus;

    /** Error message if {@link #success} is {@code false}. */
    @Schema(description = "Error message if rejudge failed")
    private String error;
    /** Stable App error code when the RPC reports a failed item. */
    @Schema(description = "App error code if rejudge failed")
    private Integer errorCode;


    /** Wall-clock time when the rejudge was initiated. ISO-8601 UTC. */
    @Schema(description = "When the rejudge was initiated (ISO-8601 UTC)")
    private Instant rejudgedAt;

    /**
     * Submission retry count after this rejudge. Increments on every
     * successful rejudge; clients can use it to detect accidental double-clicks.
     */
    @Schema(description = "Current retry count after this rejudge")
    private Integer retryCount;
}
