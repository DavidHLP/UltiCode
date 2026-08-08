package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.ProblemDifficultyCompletion;
import com.ulticode.app.api.dto.ProblemTrend;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Read port for submission-derived problem statistics.
 */
public interface ProblemSubmissionStatsPort {

    /** Total submissions created at or after {@code from}. */
    long countCreatedSince(LocalDateTime from);

    /** Accepted submissions created at or after {@code from}. */
    long countAcceptedSince(LocalDateTime from);

    /** Total submissions for a problem. */
    long countByProblemId(Long problemId);

    /** Accepted submissions for a problem. */
    long countAcceptedByProblemId(Long problemId);

    /** Per-difficulty problem completion (total vs. solved). */
    List<ProblemDifficultyCompletion> countProblemCompletionByDifficulty();

    /** Top attempted problems over the window with their accepted counts. */
    List<ProblemTrend> findTrendingProblems(LocalDateTime from, int limit);
}
