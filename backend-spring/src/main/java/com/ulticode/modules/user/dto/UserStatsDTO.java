package com.ulticode.modules.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * User statistics DTO for /users/{id}/stats endpoint.
 * Provides problem solving statistics including difficulty breakdown,
 * streak information, and submission heatmap data.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserStatsDTO {

    /**
     * Statistics by difficulty level (Easy, Medium, Hard).
     * Each entry contains count (solved) and total (available).
     */
    private Map<String, DifficultyStats> stats;

    /**
     * Current submission streak (consecutive days with submissions).
     */
    private int streak;

    /**
     * Total number of unique problems solved.
     */
    private int totalSolved;

    /**
     * Global rank based on contest rating.
     */
    private Integer globalRank;

    /**
     * Acceptance rate (percentage of submissions that were accepted).
     */
    private Double acceptanceRate;

    /**
     * Total number of submissions made by the user.
     */
    private Long submissionCount;

    /**
     * Heatmap data showing submission activity by date.
     */
    private List<HeatmapEntry> heatmap;

    /**
     * Statistics for a single difficulty level.
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DifficultyStats {
        /**
         * Number of problems solved at this difficulty.
         */
        private int count;

        /**
         * Total number of problems available at this difficulty.
         */
        private int total;

        public DifficultyStats() {}

        public DifficultyStats(int count, int total) {
            this.count = count;
            this.total = total;
        }
    }

    /**
     * A single heatmap entry representing a date with submissions.
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HeatmapEntry {
        /**
         * Date string in YYYY-MM-DD format.
         */
        private String date;

        /**
         * Activity level (0 = no activity, 1-4 = submission count tiers).
         */
        private int level;

        public HeatmapEntry() {}

        public HeatmapEntry(String date, int level) {
            this.date = date;
            this.level = level;
        }
    }
}