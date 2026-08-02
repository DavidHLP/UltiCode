package com.ulticode.modules.submission.port.adapter;

import com.ulticode.app.api.dto.DailyActiveUserCount;
import com.ulticode.app.api.dto.HourlyActiveUserCount;
import com.ulticode.app.api.dto.TopActiveUserCount;
import com.ulticode.app.api.dto.WeeklyActiveUserCount;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.port.SubmissionActivityAnalyticsPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Production adapter for {@link SubmissionActivityAnalyticsPort}, backed by
 * {@code SubmissionMapper}. Confines the active-user aggregation queries to
 * the submission module; the user analytics projection depends on the port.
 */
@Component
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
    public long countDistinctUsersInRange(LocalDateTime startDate, LocalDateTime endDate) {
        return submissionMapper.countDistinctUsersInRange(startDate, endDate);
    }
}
