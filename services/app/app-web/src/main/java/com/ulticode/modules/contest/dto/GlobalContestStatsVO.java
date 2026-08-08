package com.ulticode.modules.contest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Global contest statistics response.
 * Contains platform-wide aggregated stats (not tied to a single contest).
 */
@Schema(description = "Global contest statistics")
public record GlobalContestStatsVO(
        @Schema(description = "Number of registered participants") Integer registeredParticipants,
        @Schema(description = "Number of active participants") Integer activeParticipants,
        @Schema(description = "Number of completed participants") Integer completedParticipants,
        @Schema(description = "Total submissions across all contests") Long totalSubmissions
) {}
