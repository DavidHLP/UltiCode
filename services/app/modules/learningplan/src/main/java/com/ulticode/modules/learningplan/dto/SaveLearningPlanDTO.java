package com.ulticode.modules.learningplan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * Request body for {@code POST /learning-plans}.
 *
 * <p>The caller supplies only the fields the server stores; there is no
 * {@code userId}, {@code threadId}, {@code steps} or idempotency key on the
 * body — those are server-derived or header-supplied. Unknown JSON fields are
 * rejected before this object is bound (strict local deserialization).
 *
 * <p>{@code sourceSubmissionId} is kept as a String and validated against a
 * canonical 36-character UUID pattern so short/loose UUID forms that
 * {@code UUID.fromString} would accept are rejected at the trust boundary.
 *
 * <p>{@code title} (max 200) and {@code content} (max 16000) length limits are
 * enforced in Unicode code points by the controller, not by a UTF-16
 * {@code @Size}, so supplementary-plane characters count once as they do for
 * the Python client and in MySQL utf8mb4 storage.
 */
@Data
public class SaveLearningPlanDTO {

    /** Canonical UUID of the submission the plan was derived from. */
    @NotBlank(message = "sourceSubmissionId is required")
    @Pattern(
            regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
            message = "sourceSubmissionId must be a canonical UUID")
    private String sourceSubmissionId;

    /** Caller-confirmed draft version; must be a positive integer. */
    @NotNull(message = "draftVersion is required")
    @Positive(message = "draftVersion must be positive")
    private Integer draftVersion;

    @NotBlank(message = "title is required")
    private String title;

    @NotBlank(message = "content is required")
    private String content;
}
