package com.ulticode.modules.submission.port.adapter;

import com.ulticode.submission.api.dto.DailyActiveUserCount;
import com.ulticode.submission.api.dto.HourlyActiveUserCount;
import com.ulticode.submission.api.dto.TopActiveUserCount;
import com.ulticode.submission.api.dto.WeeklyActiveUserCount;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.submission.api.service.SubmissionActivityAnalyticsPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Production adapter for {@link SubmissionActivityAnalyticsPort}, backed by
 * {@code SubmissionMapper}. Confines the active-user aggregation queries to
 * the submission module; the user analytics projection depends on the port.
 */
@Component
@Primary
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
        "'${app.runtime.mode:dev-lite}' == 'legacy-rollback'")
@RequiredArgsConstructor
public class SubmissionActivityAnalyticsMapperAdapter implements SubmissionActivityAnalyticsPort {

    private final SubmissionMapper submissionMapper;

    @Override
    public List<DailyActiveUserCount> countDailyActiveUsers(LocalDateTime startDate, LocalDateTime endDate) {
        return submissionMapper.countDailyActiveUsers(startDate, endDate);
    }

    @Override
    public List<WeeklyActiveUserCount> countWeeklyActiveUsers(LocalDateTime startDate) {
        return submissionMapper.countWeeklyActiveUsers(startDate);
    }

    @Override
    public List<HourlyActiveUserCount> countActiveUsersByHour(LocalDateTime startDate) {
        return submissionMapper.countActiveUsersByHour(startDate);
    }

    @Override
    public List<TopActiveUserCount> findTopActiveUsers(LocalDateTime startDate, int limit) {
        return submissionMapper.findTopActiveUsers(startDate, limit);
    }

    @Override
    public Long countActiveUsersBetween(LocalDateTime startDate, LocalDateTime endDate) {
        return submissionMapper.countDistinctUsersInRange(startDate, endDate);
    }
}
