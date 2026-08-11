package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.dto.DailyActiveUserCount;
import com.ulticode.app.api.dto.HourlyActiveUserCount;
import com.ulticode.app.api.dto.TopActiveUserCount;
import com.ulticode.app.api.dto.WeeklyActiveUserCount;
import com.ulticode.app.api.service.SubmissionActivityAnalyticsPort;
import com.ulticode.modules.submission.port.adapter.SubmissionActivityAnalyticsMapperAdapter;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Dubbo provider for App-owned submission activity analytics.
 */
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class SubmissionActivityAnalyticsProvider implements SubmissionActivityAnalyticsPort {

    private final SubmissionActivityAnalyticsMapperAdapter delegate;

    @Override
    public List<DailyActiveUserCount> countDailyActiveUsers(LocalDateTime startDate, LocalDateTime endDate) {
        return delegate.countDailyActiveUsers(startDate, endDate);
    }

    @Override
    public List<WeeklyActiveUserCount> countWeeklyActiveUsers(LocalDateTime startDate) {
        return delegate.countWeeklyActiveUsers(startDate);
    }

    @Override
    public List<HourlyActiveUserCount> countActiveUsersByHour(LocalDateTime startDate) {
        return delegate.countActiveUsersByHour(startDate);
    }

    @Override
    public List<TopActiveUserCount> findTopActiveUsers(LocalDateTime startDate, int limit) {
        return delegate.findTopActiveUsers(startDate, limit);
    }

    @Override
    public Long countActiveUsersBetween(LocalDateTime startDate, LocalDateTime endDate) {
        return delegate.countActiveUsersBetween(startDate, endDate);
    }
}
