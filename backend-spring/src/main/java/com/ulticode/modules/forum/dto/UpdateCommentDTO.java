package com.ulticode.modules.forum.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for updating an existing forum comment.
 */
@Data
@Schema(description = "Update forum comment request")
public class UpdateCommentDTO {

    @NotBlank(message = "Comment body is required")
    @Size(max = 10000, message = "Comment body must not exceed 10000 characters")
    @Schema(description = "Comment body content", example = "Updated comment content")
    private String body;
}
