package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.dto.DailyActiveUserCount;
import com.ulticode.app.api.dto.HourlyActiveUserCount;
import com.ulticode.app.api.dto.TopActiveUserCount;
import com.ulticode.app.api.dto.WeeklyActiveUserCount;
import com.ulticode.app.api.service.SubmissionActivityAnalyticsPort;
import com.ulticode.common.rpc.RpcPolicy;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin consumer adapter for App-owned submission activity analytics.
 */
@Primary
@Component
public class DubboSubmissionActivityAnalyticsAdapter implements SubmissionActivityAnalyticsPort {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private SubmissionActivityAnalyticsPort submissionActivityAnalyticsPort;

    @Override
    public List<DailyActiveUserCount> countDailyActiveUsers(LocalDateTime startDate, LocalDateTime endDate) {
        return submissionActivityAnalyticsPort.countDailyActiveUsers(startDate, endDate);
    }

    @Override
    public List<WeeklyActiveUserCount> countWeeklyActiveUsers(LocalDateTime startDate) {
        return submissionActivityAnalyticsPort.countWeeklyActiveUsers(startDate);
    }

    @Override
    public List<HourlyActiveUserCount> countActiveUsersByHour(LocalDateTime startDate) {
        return submissionActivityAnalyticsPort.countActiveUsersByHour(startDate);
    }

    @Override
    public List<TopActiveUserCount> findTopActiveUsers(LocalDateTime startDate, int limit) {
        return submissionActivityAnalyticsPort.findTopActiveUsers(startDate, limit);
    }

    @Override
    public Long countActiveUsersBetween(LocalDateTime startDate, LocalDateTime endDate) {
        Long count = submissionActivityAnalyticsPort.countActiveUsersBetween(startDate, endDate);
        return count == null ? 0L : count;
    }
}
