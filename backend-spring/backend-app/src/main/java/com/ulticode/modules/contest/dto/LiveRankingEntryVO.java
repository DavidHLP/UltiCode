package com.ulticode.modules.contest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Response DTO for live contest ranking entries.
 * Contains only fields needed for real-time ranking display.
 */
@Schema(description = "Live contest ranking entry")
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LiveRankingEntryVO {

    @Schema(description = "User rank in the contest")
    private Integer rank;

    @Schema(description = "User ID")
    private String userId;

    @Schema(description = "Username")
    private String username;

    @Schema(description = "User display name")
    private String name;

    @Schema(description = "User avatar URL")
    private String avatar;

    @Schema(description = "User score")
    private Long score;

    @Schema(description = "Penalty time")
    private Long penalty;

    @Schema(description = "Number of problems solved")
    private Integer problemsSolved;

    @Schema(description = "Whether this is the current user")
    private Boolean isCurrentUser;
}
