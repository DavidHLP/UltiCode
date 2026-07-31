package com.ulticode.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @Size(max = 100, message = "Cannot process more than 100 comments at once")
    private List<String> ids;

    /**
     * Comment type: "forum" or "solution"
     */
    @NotBlank(message = "Comment type cannot be blank")
    @Pattern(regexp = "forum|solution", message = "Type must be 'forum' or 'solution'")
    private String type;

    /**
     * Action to perform: "delete" or "unflag"
     */
    @NotBlank(message = "Action cannot be blank")
    @Pattern(regexp = "delete|unflag", message = "Action must be 'delete' or 'unflag'")
    private String action;
}
