package com.ulticode.modules.admin.port.adapter;

import com.ulticode.modules.admin.port.AdminUserStatsReadPort;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.stats.SubmissionStreakCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Production adapter for {@link AdminUserStatsReadPort}.
 *
 * <p>Backed by {@code SubmissionMapper} + {@code SolutionMapper}. Coerces
 * the nullable {@code Long}/{@code Integer} mapper returns to primitives so
 * the port interface stays null-free — the only place in the admin module
 * that touches these two mappers. Tests substitute a fixture by providing
 * another bean of the port interface; admin never sees the mappers.
 *
 * @author ulticode
 */
@Component
@RequiredArgsConstructor
public class AdminUserStatsReadAdapter implements AdminUserStatsReadPort {

    private final SubmissionMapper submissionMapper;
    private final SubmissionStreakCalculator submissionStreakCalculator;
    private final SolutionMapper solutionMapper;

    @Override
    public long countSubmissionsByUserId(String userId) {
        Long n = submissionMapper.countByUserId(userId);
        return n == null ? 0L : n;
    }

    @Override
    public long countAcceptedProblemsByUserId(String userId) {
        Long n = submissionMapper.countAcceptedProblemsByUserId(userId);
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
