package com.ulticode.modules.admin.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Request body for batch rejudge operation.
 *
 * <p>Field {@link #submissionIds} is the canonical name. The legacy alias {@code ids}
 * is still accepted for backward compatibility with clients that send the old
 * spec, but new integrations should use {@code submissionIds}.</p>
 */
@Data
@Schema(description = "Batch rejudge request")
public class BatchRejudgeRequest {

    /**
     * List of submission IDs to rejudge.
     * <p>Must be non-empty and contain at most 50 entries — enforced via Bean
     * Validation; oversized batches are rejected with HTTP 400 before reaching
     * the service layer.</p>
     */
    @NotEmpty(message = "submissionIds must not be empty")
    @Size(max = 50, message = "submissionIds size must not exceed 50")
    @JsonAlias({"ids"})
    @Schema(description = "List of submission IDs to rejudge", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> submissionIds;

    /**
     * Whether to notify the affected users via email/in-app notification
     * after the rejudge completes. Defaults to {@code false} to avoid spamming
     * users when admins run bulk operations.
     */
    @Schema(description = "Whether to notify users about the rejudge", defaultValue = "false")
    private Boolean notifyUsers = false;
}
