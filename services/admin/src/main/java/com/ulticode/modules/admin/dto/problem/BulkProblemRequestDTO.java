package com.ulticode.modules.admin.dto.problem;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * DTO for bulk problem operations.
 */
@Data
@Schema(description = "Bulk problem operation request")
public class BulkProblemRequestDTO {

    public static final int MAX_BULK_SIZE = 500;

    @NotEmpty(message = "IDs list cannot be empty")
    @Size(max = MAX_BULK_SIZE, message = "Cannot process more than " + MAX_BULK_SIZE + " IDs at once")
    @Schema(description = "List of problem IDs to operate on (max " + MAX_BULK_SIZE + ")")
    private List<String> ids;

    @NotNull(message = "Action is required")
    @Schema(description = "Bulk action to perform", allowableValues = {"publish", "unpublish", "delete", "restore", "edit"})
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
        restore,
        edit
    }
}
