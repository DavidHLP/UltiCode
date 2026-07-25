package com.ulticode.modules.contest.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Slim VO for contest list responses.
 * Excludes heavy fields (description, problemIds, tags, userScore, etc.) to reduce payload size.
 */
@Schema(description = "Contest list item view object")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContestListVO(
        @Schema(description = "Contest unique identifier") String id,
        @Schema(description = "URL-friendly identifier for the contest") String slug,
        @Schema(description = "Contest title") String title,
        @Schema(description = "Contest status: DRAFT, UPCOMING, RUNNING, FINISHED, CANCELLED") String status,
        @Schema(description = "Contest start time") @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime startTime,
        @Schema(description = "Contest end time") @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime endTime,
        @Schema(description = "Contest duration in minutes") Integer duration,
        @Schema(description = "Contest type/format: ICPC, IOI, CUSTOM") String contestType,
        @Schema(description = "Number of participants") Integer participantCount,
        @Schema(description = "Number of problems in the contest") Integer problemCount,
        @Schema(description = "Whether this is a premium contest") Boolean isPremium,
        @Schema(description = "Whether the contest is published") Boolean isPublished,
        @Schema(description = "Whether the contest is visible to users") Boolean isVisible,
        @Schema(description = "Maximum number of participants") Integer maxParticipants,
        @Schema(description = "Current number of registered participants") Integer registeredCount,
        @Schema(description = "Whether the user is participating in this contest") Boolean isParticipating,
        @Schema(description = "User's ranking in the contest") Integer userRanking,
        @Schema(description = "Whether this is a rated contest") Boolean isRated,
        @Schema(description = "Scoring mode: SCORE, ICPC, IOI") String scoringMode,
        @Schema(description = "Penalty seconds per wrong submission") Integer penaltyPerWrong,
        @Schema(description = "Cover image URL") String coverImage
) {}