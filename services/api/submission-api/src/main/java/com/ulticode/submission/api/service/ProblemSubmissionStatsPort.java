package com.ulticode.submission.api.service;

import com.ulticode.submission.api.dto.ProblemDifficultyCompletion;
import com.ulticode.submission.api.dto.ProblemTrend;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    /** Total submissions grouped by problem id in one bounded owner read. */
    Map<Long, Long> countByProblemIds(List<Long> problemIds);

    /** Accepted submissions grouped by problem id in one bounded owner read. */
    Map<Long, Long> countAcceptedByProblemIds(List<Long> problemIds);

    /** Completion by the supplied App-owned problem difficulty facts. */
    List<ProblemDifficultyCompletion> countProblemCompletionByDifficulty(
            Map<Long, String> difficultyByProblemId);

    /** Top attempted problems over the window with their accepted counts. */
    List<ProblemTrend> findTrendingProblems(LocalDateTime from, int limit);
}
