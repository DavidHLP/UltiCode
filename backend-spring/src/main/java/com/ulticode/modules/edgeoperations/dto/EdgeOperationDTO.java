package com.ulticode.modules.edgeoperations.dto;

import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import com.ulticode.modules.vote.entity.enums.EdgeOperationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * DTO for edge operations.
 * Supports operations like voting, analyzing, viewing, etc.
 */
@Data
@Schema(description = "Edge operation request DTO")
public class EdgeOperationDTO {

    /**
     * ID of the target to operate on
     */
    @NotNull(message = "Target ID is required")
    @Schema(description = "ID of the target to operate on", required = true)
    private String targetId;

    /**
     * Type of the target
     */
    @NotNull(message = "Target type is required")
    @Schema(description = "Type of the target", required = true)
    private EdgeOperationTargetType targetType;

    /**
     * Type of operation to perform
     */
    @NotNull(message = "Operation type is required")
    @Schema(description = "Type of operation to perform", required = true)
    private EdgeOperationType operationType;
}
