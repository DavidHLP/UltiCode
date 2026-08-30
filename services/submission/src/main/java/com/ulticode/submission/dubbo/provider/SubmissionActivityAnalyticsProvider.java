package com.ulticode.submission.dubbo.provider;

import com.ulticode.submission.api.dto.DailyActiveUserCount;
import com.ulticode.submission.api.dto.HourlyActiveUserCount;
import com.ulticode.submission.api.dto.TopActiveUserCount;
import com.ulticode.submission.api.dto.WeeklyActiveUserCount;
import com.ulticode.submission.api.service.SubmissionActivityAnalyticsPort;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.time.LocalDateTime;
import java.util.List;

/** Exposes Submission-owner active-user analytics. */
@DubboService(group = "backend-submission", version = "1.0.0")
@RequiredArgsConstructor
public class SubmissionActivityAnalyticsProvider implements SubmissionActivityAnalyticsPort {

    private final SubmissionMapper submissionMapper;

    @Override
    public List<DailyActiveUserCount> countDailyActiveUsers(LocalDateTime startDate, LocalDateTime endDate) {
        return safe(submissionMapper.countDailyActiveUsers(startDate, endDate));
    }

    @Override
    public List<WeeklyActiveUserCount> countWeeklyActiveUsers(LocalDateTime startDate) {
        return safe(submissionMapper.countWeeklyActiveUsers(startDate));
    }

    @Override
    public List<HourlyActiveUserCount> countActiveUsersByHour(LocalDateTime startDate) {
        return safe(submissionMapper.countActiveUsersByHour(startDate));
    }

    @Override
    public List<TopActiveUserCount> findTopActiveUsers(LocalDateTime startDate, int limit) {
        return safe(submissionMapper.findTopActiveUsers(startDate, limit));
    }

    @Override
    public Long countActiveUsersBetween(LocalDateTime startDate, LocalDateTime endDate) {
        return submissionMapper.countDistinctUsersInRange(startDate, endDate);
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
