package com.ulticode.modules.admin.port.adapter;

import com.ulticode.modules.admin.port.AdminUserStatsReadPort;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import com.ulticode.modules.submission.port.SubmissionUserStatsPort;
import com.ulticode.modules.submission.stats.SubmissionStreakCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Production adapter for {@link AdminUserStatsReadPort}.
 *
 * <p>Backed by {@link SubmissionUserStatsPort} + {@code SolutionMapper}. Coerces
 * the nullable {@code Long}/{@code Integer} returns to primitives so
 * the port interface stays null-free — the admin module depends on the
 * submission read port, not the submission mapper. Tests substitute a fixture
 * by providing another bean of the port interface.
 *
 * @author ulticode
 */
@Component
@RequiredArgsConstructor
public class AdminUserStatsReadAdapter implements AdminUserStatsReadPort {

    private final SubmissionUserStatsPort submissionUserStats;
    private final SubmissionStreakCalculator submissionStreakCalculator;
    private final SolutionMapper solutionMapper;

    @Override
    public long countSubmissionsByUserId(String userId) {
        Long n = submissionUserStats.countByUserId(userId);
        return n == null ? 0L : n;
    }

    @Override
    public long countAcceptedProblemsByUserId(String userId) {
        Long n = submissionUserStats.countAcceptedProblemsByUserId(userId);
        return n == null ? 0L : n;
    }

    @Override
    public long countSolutionsByUserId(String userId) {
        Long n = solutionMapper.countByUserId(userId);
        return n == null ? 0L : n;
    }

    @Override
    public int calculateSubmissionStreak(String userId) {
        return submissionStreakCalculator.computeStreak(userId);
    }
}
