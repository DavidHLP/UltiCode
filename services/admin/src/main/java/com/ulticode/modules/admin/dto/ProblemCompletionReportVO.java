package com.ulticode.modules.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Problem completion report exposed by the administrator HTTP API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemCompletionReportVO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DifficultyStats {
        private String difficulty;
        private Integer total;
        private Integer completed;
        private Double rate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TagStats {
        private String tagId;
        private String label;
        private Integer total;
        private Integer completed;
        private Double rate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendingProblem {
        private String problemId;
        private String title;
        private Integer attempts;
        private Double completionRate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HardestProblem {
        private String problemId;
        private String title;
        private String difficulty;
        private Double completionRate;
    }

    private Long totalAttempts;
    private Long successfulAttempts;
    private Double overallCompletionRate;
    private List<DifficultyStats> byDifficulty;
    private List<TagStats> byTag;
    private List<TrendingProblem> trendingProblems;
    private List<HardestProblem> hardestProblems;
}
