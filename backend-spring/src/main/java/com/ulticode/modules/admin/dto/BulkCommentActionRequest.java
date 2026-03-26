package com.ulticode.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * Request DTO for bulk action on comments.
 */
@Data
public class BulkCommentActionRequest {

    /**
     * List of comment IDs to perform action on
     */
    @NotEmpty(message = "Comment IDs cannot be empty")
    private List<String> ids;

    /**
     * Comment type: "forum" or "solution"
     */
    @NotBlank(message = "Comment type cannot be blank")
    private String type;

    /**
     * Action to perform: "delete" or "unflag"
     */
    @NotBlank(message = "Action cannot be blank")
    private String action;
}
