package com.ulticode.modules.forum.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for creating a new forum comment.
 */
@Data
@Schema(description = "Create forum comment request")
public class CreateCommentDTO {

    @NotBlank(message = "Comment body is required")
    @Size(max = 10000, message = "Comment body must not exceed 10000 characters")
    @Schema(description = "Comment body content", example = "Thanks for the detailed explanation!")
    private String body;

    @Schema(description = "Parent comment ID for replies", example = "uuid-of-parent-comment")
    private String parentId;
}
