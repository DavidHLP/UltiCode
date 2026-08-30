package com.ulticode.modules.submission.port.adapter;

import com.ulticode.submission.api.service.SubmissionStreakPort;
import com.ulticode.modules.submission.stats.SubmissionStreakCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Production adapter for {@link SubmissionStreakPort}, delegating to
 * {@link SubmissionStreakCalculator#computeStreak}.
 */
@Component
@Primary
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
        "'${app.runtime.mode:dev-lite}' == 'legacy-rollback'")
@RequiredArgsConstructor
public class SubmissionStreakAdapter implements SubmissionStreakPort {

    private final SubmissionStreakCalculator streakCalculator;

    @Override
    public int computeStreak(String userId) {
        return streakCalculator.computeStreak(userId);
    }
}
