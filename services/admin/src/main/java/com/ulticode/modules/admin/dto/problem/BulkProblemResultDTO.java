package com.ulticode.modules.admin.dto.problem;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for bulk operation result per item.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Bulk operation result for a single item")
public class BulkProblemResultDTO {

    @Schema(description = "Problem ID")
    private String id;

    @Schema(description = "Whether the operation succeeded")
    private boolean success;

    @Schema(description = "Error message if operation failed")
    private String error;
}
