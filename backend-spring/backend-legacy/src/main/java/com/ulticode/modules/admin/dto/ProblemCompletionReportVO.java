package com.ulticode.modules.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Problem Completion Report View Object.
 * Contains submission statistics, completion rates by difficulty and tags.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemCompletionReportVO {

    /**
     * Completion rate by difficulty.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DifficultyStats {
        private String difficulty;
        private Integer total;
        private Integer completed;
        private Double rate;  // Completion rate (%)
    }

    /**
     * Completion rate by tag.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TagStats {
        private String tagId;
        private String label;
        private Integer total;
        private Integer completed;
        private Double rate;  // Completion rate (%)
    }

    /**
     * Trending problem data point.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendingProblem {
        private String problemId;
        private String title;
        private Integer attempts;
        private Double completionRate;
    }

    /**
     * Hardest problem data point.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HardestProblem {
        private String problemId;
        private String title;
        private String difficulty;
        private Double completionRate;
    }

    /**
     * Total submission attempts.
     */
    private Long totalAttempts;

    /**
     * Successful (accepted) attempts.
     */
    private Long successfulAttempts;

    /**
     * Overall completion rate (%).
     */
    private Double overallCompletionRate;

    /**
     * Completion stats by difficulty level.
     */
    private List<DifficultyStats> byDifficulty;

    /**
     * Completion stats by tag.
     */
    private List<TagStats> byTag;

    /**
     * Trending problems (most attempted).
     */
    private List<TrendingProblem> trendingProblems;

    /**
     * Hardest problems (lowest completion rate).
     */
    private List<HardestProblem> hardestProblems;
}
