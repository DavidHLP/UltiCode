package com.ulticode.app.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * Trending-problem aggregation row: problem id, total attempts, and accepted
 * attempts over the window. Backs {@code findTrendingProblems}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProblemTrend implements Serializable {
    private Long problemId;
    private Long attemptCount;
    private Long acceptedCount;
}
