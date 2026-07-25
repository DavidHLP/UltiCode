package com.ulticode.modules.submission.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Trending-problem aggregation row: problem id, total attempts, and accepted
 * attempts over the window. Backs {@code findTrendingProblems}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProblemTrend {
    private Long problemId;
    private Long attemptCount;
    private Long acceptedCount;
}
