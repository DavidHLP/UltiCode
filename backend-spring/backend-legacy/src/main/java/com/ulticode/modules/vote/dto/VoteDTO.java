package com.ulticode.modules.vote.dto;

import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * DTO for vote operations.
 * Supports three-state voting: 1 (upvote), -1 (downvote), 0 (neutral/remove vote)
 */
@Data
@Schema(description = "Vote request DTO")
public class VoteDTO {

    /**
     * ID of the target to vote on
     */
    @NotNull(message = "Target ID is required")
    @Schema(description = "ID of the target to vote on", required = true)
    private String targetId;

    /**
     * Type of the target
     */
    @NotNull(message = "Target type is required")
    @Schema(description = "Type of the target", required = true)
    private EdgeOperationTargetType targetType;

    /**
     * Vote value: 1 (upvote), -1 (downvote), 0 (neutral/remove vote)
     */
    @NotNull(message = "Vote value is required")
    @Schema(description = "Vote value: 1 (upvote), -1 (downvote), 0 (neutral/remove vote)", required = true)
    private Integer value;
}
