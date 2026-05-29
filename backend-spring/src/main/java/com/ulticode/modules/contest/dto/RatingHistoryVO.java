package com.ulticode.modules.contest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Response DTO for user rating history entries.
 * Contains rating change information per contest.
 */
@Schema(description = "User rating history entry")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RatingHistoryVO(
        @Schema(description = "Contest unique identifier") String contestId,
        @Schema(description = "Contest title") String title,
        @Schema(description = "Contest slug") String slug,
        @Schema(description = "Rating change amount") Integer ratingChange,
        @Schema(description = "New rating after contest") Integer newRating,
        @Schema(description = "Old rating before contest") Integer oldRating,
        @Schema(description = "When the rating was updated") LocalDateTime ratedAt,
        @Schema(description = "Performance rating for this contest") Integer performance
) {}
