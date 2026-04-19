package com.ulticode.modules.admin.dto.problem;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * DTO for bulk problem operations.
 */
@Data
@Schema(description = "Bulk problem operation request")
public class BulkProblemRequestDTO {

    @NotEmpty(message = "IDs list cannot be empty")
    @Schema(description = "List of problem IDs to operate on")
    private List<String> ids;

    @NotNull(message = "Action is required")
    @Schema(description = "Bulk action to perform", allowableValues = {"publish", "unpublish", "delete", "edit"})
    private BulkAction action;

    @Schema(description = "Optional parameters for edit action, e.g., { difficulty: \"Easy\" }")
    private Map<String, Object> params;

    /**
     * Bulk action enum.
     */
    public enum BulkAction {
        publish,
        unpublish,
        delete,
        edit
    }
}
