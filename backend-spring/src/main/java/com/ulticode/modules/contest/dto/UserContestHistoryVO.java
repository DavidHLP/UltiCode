package com.ulticode.modules.contest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Response DTO for user contest participation history.
 * Contains contest info and user's performance summary.
 */
@Schema(description = "User contest history entry")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserContestHistoryVO(
        @Schema(description = "Contest unique identifier") String contestId,
        @Schema(description = "Contest title") String title,
        @Schema(description = "Contest slug") String slug,
        @Schema(description = "Contest start time") LocalDateTime startTime,
        @Schema(description = "User's finish time") LocalDateTime finishTime,
        @Schema(description = "User rank in the contest") Integer rank,
        @Schema(description = "User score") Long score,
        @Schema(description = "Penalty time") Long penalty,
        @Schema(description = "Number of problems solved") Integer problemsSolved,
        @Schema(description = "Total participants in the contest") Integer totalParticipants,
        @Schema(description = "Whether the contest is rated") Boolean isRated
) {}
