package com.ulticode.modules.admin.port.adapter;

import com.ulticode.modules.admin.port.AdminUserStatsReadPort;
import com.ulticode.app.api.service.SubmissionStreakPort;
import com.ulticode.app.api.service.SubmissionUserStatsPort;
import com.ulticode.app.api.service.SolutionReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Production adapter for {@link AdminUserStatsReadPort}.
 *
 * <p>Backed by {@link SubmissionUserStatsPort} + {@link SolutionReadPort} +
 * {@link SubmissionStreakPort}. Coerces the nullable {@code Long}/{@code Integer}
 * returns to primitives so the port interface stays null-free — the admin
 * module depends on app-api read ports, not on concrete mappers or stats
 * classes. Tests substitute a fixture by providing another bean of the port
 * interface.
 *
 * @author ulticode
 */
@Component
@RequiredArgsConstructor
public class AdminUserStatsReadAdapter implements AdminUserStatsReadPort {

    private final SubmissionUserStatsPort submissionUserStats;
    private final SubmissionStreakPort submissionStreakPort;
    private final SolutionReadPort solutionReadPort;

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
        return solutionReadPort.countByUserId(userId);
    }

    @Override
    public int calculateSubmissionStreak(String userId) {
        return submissionStreakPort.computeStreak(userId);
    }
}
