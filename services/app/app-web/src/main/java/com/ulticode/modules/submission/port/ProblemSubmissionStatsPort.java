package com.ulticode.modules.submission.port;

import com.ulticode.submission.api.dto.ProblemDifficultyCompletion;
import com.ulticode.submission.api.dto.ProblemTrend;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Read port for submission-derived problem statistics, consumed by the problem
 * module's analytics/detail projections. The submission module owns this port
 * so problem-side callers depend on a typed seam rather than
 * {@code SubmissionMapper} (mirroring {@link SubmissionUserStatsPort}).
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
