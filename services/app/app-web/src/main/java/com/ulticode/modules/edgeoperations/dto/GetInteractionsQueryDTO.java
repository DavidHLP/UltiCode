package com.ulticode.modules.edgeoperations.dto;

import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * DTO for querying interaction stats.
 */
@Data
@Schema(description = "Query parameters for getting interactions")
public class GetInteractionsQueryDTO {

    /**
     * ID of the target
     */
    @NotNull(message = "Target ID is required")
    @Schema(description = "ID of the target", required = true)
    private String targetId;

    /**
     * Type of the target
     */
    @NotNull(message = "Target type is required")
    @Schema(description = "Type of the target", required = true)
    private EdgeOperationTargetType targetType;
}
