package com.ulticode.modules.admin.service.impl;

import com.ulticode.modules.admin.dto.UserActivityReportVO;
import com.ulticode.modules.admin.mapper.AuditLogMapper;
import com.ulticode.modules.admin.service.AdminUserAnalyticsService;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of AdminUserAnalyticsService.
 * Handles user activity, retention, and engagement analytics.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserAnalyticsServiceImpl implements AdminUserAnalyticsService {

    private final Clock clock;
    private final UserMapper userMapper;
    private final SubmissionMapper submissionMapper;
    private final AuditLogMapper auditLogMapper;

    @Override
    public UserActivityReportVO getUserActivityReport(Integer days) {
        int daysToAnalyze = days != null && days > 0 ? days : 30;
        LocalDateTime startDate = LocalDateTime.now(clock).minusDays(daysToAnalyze);

        UserActivityReportVO report = new UserActivityReportVO();

        // Daily active users - based on submissions (more representative than audit_logs
        // which only records admin operations)
        List<UserActivityReportVO.DailyActiveUsers> dailyActiveUsers = new ArrayList<>();
        LocalDateTime overallStart = LocalDateTime.now(clock).minusDays(daysToAnalyze).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime overallEnd = LocalDateTime.now(clock).plusDays(1).withHour(0).withMinute(0).withSecond(0);
        List<Map<String, Object>> dailyCounts = submissionMapper.countDailyActiveUsers(overallStart, overallEnd);
        for (Map<String, Object> row : dailyCounts) {
            dailyActiveUsers.add(new UserActivityReportVO.DailyActiveUsers(
                    row.get("date").toString(),
                    ((Number) row.get("count")).intValue()
            ));
        }
        report.setActiveUsersDaily(dailyActiveUsers);

        // Weekly active users - single aggregation query replacing per-week N+1 loop
        List<UserActivityReportVO.DailyActiveUsers> weeklyActiveUsers = new ArrayList<>();
        List<Map<String, Object>> weeklyCounts = submissionMapper.countWeeklyActiveUsers(startDate);
        for (Map<String, Object> row : weeklyCounts) {
            String weekStart = row.get("week_start") != null
                    ? row.get("week_start").toString()
                    : row.get("yearweek").toString();
            int count = ((Number) row.get("count")).intValue();
            weeklyActiveUsers.add(new UserActivityReportVO.DailyActiveUsers(weekStart, count));
        }
        report.setActiveUsersWeekly(weeklyActiveUsers);

        // User retention (simplified calculation)
        UserActivityReportVO.UserRetention retention = new UserActivityReportVO.UserRetention();
        retention.setDay1(calculateRetentionRate(1));
        retention.setDay7(calculateRetentionRate(7));
        retention.setDay30(calculateRetentionRate(30));
        report.setUserRetention(retention);

        // Peak active hours - single aggregation query replacing 24 individual COUNT queries
        List<UserActivityReportVO.PeakActiveHour> peakHours = new ArrayList<>();
        List<Map<String, Object>> hourCounts = submissionMapper.countActiveUsersByHour(LocalDateTime.now(clock).minusDays(30));
        for (Map<String, Object> row : hourCounts) {
            int hour = ((Number) row.get("hour")).intValue();
            int count = ((Number) row.get("count")).intValue();
            peakHours.add(new UserActivityReportVO.PeakActiveHour(hour, count));
        }
        peakHours.sort((a, b) -> Long.compare(b.getCount(), a.getCount()));
        report.setPeakActiveHours(peakHours.stream().limit(24).collect(Collectors.toList()));

        // Top active users - single aggregation query replacing load-all + Java groupBy + N user lookups
        List<UserActivityReportVO.TopActiveUser> topUsers = new ArrayList<>();
        List<Map<String, Object>> topUserCounts = submissionMapper.findTopActiveUsers(startDate, 10);
        for (Map<String, Object> row : topUserCounts) {
            String userId = row.get("user_id").toString();
            int count = ((Number) row.get("submission_count")).intValue();
            User user = userMapper.selectById(userId);
            topUsers.add(new UserActivityReportVO.TopActiveUser(
                    userId,
                    user != null ? user.getUsername() : "Unknown",
                    count,
                    user != null ? user.getLastLoginAt() : null
            ));
        }
        report.setTopActiveUsers(topUsers);

        // Average session duration (default value)
        report.setAverageSessionDuration(300.0); // 5 minutes default

        return report;
    }

    // ==================== Private Helper Methods ====================

    /**
     * Calculate retention rate for a given day.
     * Uses COUNT(DISTINCT user_id) aggregation queries for accurate distinct user counting.
     * Previous implementation used selectCount with groupBy which returns the count of the
     * first group only, not the total distinct user count (MyBatis-Plus Pitfall 4).
     *
     * NOTE: This is an approximation using distinct user counts rather than
     * a true set intersection. For exact retention, a dedicated
     * subquery-based approach or materialized view is needed.
     */
    private Double calculateRetentionRate(int dayN) {
        LocalDateTime day0 = LocalDateTime.now(clock).minusDays(dayN);
        LocalDateTime dayNDate = day0.plusDays(dayN);

        LocalDateTime day0Start = day0.withHour(0).withMinute(0).withSecond(0);
        LocalDateTime day0End = day0Start.plusDays(1);
        LocalDateTime dayNStart = dayNDate.withHour(0).withMinute(0).withSecond(0);
        LocalDateTime dayNEnd = dayNStart.plusDays(1);

        long day0DistinctUsers = submissionMapper.countDistinctUsersInRange(day0Start, day0End);

        if (day0DistinctUsers == 0) {
            return 0.0;
        }

        long dayNDistinctUsers = submissionMapper.countDistinctUsersInRange(dayNStart, dayNEnd);

        // Approximate retention: ratio of distinct active users on day N vs day 0
        return Math.min(dayNDistinctUsers * 100.0 / day0DistinctUsers, 100.0);
    }
}
