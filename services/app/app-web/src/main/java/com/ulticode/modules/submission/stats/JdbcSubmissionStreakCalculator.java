package com.ulticode.modules.submission.stats;

import com.ulticode.modules.submission.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Default adapter of {@link SubmissionStreakCalculator}.
 *
 * <p>Delegates to {@link SubmissionMapper#calculateStreak(String)} (the
 * recursive-CTE query that produces {@code MIN(days_ago)}) and collapses
 * the mapper's nullable {@code Integer} return into the primitive
 * {@code int} the interface promises. The SQL stays in the mapper — that
 * is the only place it can run — but every other layer now consumes the
 * interface, so the null->{@code 0} contract is centralised here rather
 * than re-implemented at every call site.
 *
 * <p>Why {@code @Component} and not {@code @Service}: matches the
 * {@link DefaultSubmissionPerformanceStats} pattern in the same package
 * (deep module adapters under {@code submission/stats/} are typed as
 * infrastructure collaborators, not business services).
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
        "'${app.runtime.mode:dev-lite}' == 'legacy-rollback'")
@RequiredArgsConstructor
public class JdbcSubmissionStreakCalculator implements SubmissionStreakCalculator {

    private final SubmissionMapper submissionMapper;

    @Override
    public int computeStreak(String userId) {
        Integer streak = submissionMapper.calculateStreak(userId);
        return streak == null ? 0 : streak;
    }
}
