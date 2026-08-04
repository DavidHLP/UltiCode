package com.ulticode.app.api.dto;

import java.util.List;
import java.io.Serializable;

/**
 * Entity-free Problem completion report returned by the Problem provider.
 *
 * <p>Numeric values use zero for an empty aggregation and every collection is
 * normalized to a non-null immutable list. The admin edge maps this shape to
 * its existing HTTP response type.
 */
public record ProblemCompletionReportDTO(
        long totalAttempts,
        long successfulAttempts,
        double overallCompletionRate,
        List<DifficultyStats> byDifficulty,
        List<TagStats> byTag,
        List<TrendingProblem> trendingProblems,
        List<HardestProblem> hardestProblems) implements Serializable {

    public ProblemCompletionReportDTO {
        byDifficulty = byDifficulty == null ? List.of() : List.copyOf(byDifficulty);
        byTag = byTag == null ? List.of() : List.copyOf(byTag);
        trendingProblems = trendingProblems == null ? List.of() : List.copyOf(trendingProblems);
        hardestProblems = hardestProblems == null ? List.of() : List.copyOf(hardestProblems);
    }

    /** Completion rate grouped by difficulty. */
    public record DifficultyStats(String difficulty, int total, int completed, double rate) implements Serializable {}

    /** Completion rate grouped by tag. */
    public record TagStats(String tagId, String label, int total, int completed, double rate) implements Serializable {}

    /** Most-attempted Problem data point. */
    public record TrendingProblem(String problemId, String title, int attempts,
                                  double completionRate) implements Serializable {}

    /** Lowest-completion-rate Problem data point. */
    public record HardestProblem(String problemId, String title, String difficulty,
                                 double completionRate) implements Serializable {}
}
