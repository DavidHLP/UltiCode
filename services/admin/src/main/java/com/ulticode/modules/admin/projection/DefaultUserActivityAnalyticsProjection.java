package com.ulticode.modules.admin.projection;

import com.ulticode.submission.api.dto.DailyActiveUserCount;
import com.ulticode.submission.api.dto.HourlyActiveUserCount;
import com.ulticode.submission.api.dto.TopActiveUserCount;
import com.ulticode.submission.api.dto.WeeklyActiveUserCount;
import com.ulticode.submission.api.service.SubmissionActivityAnalyticsPort;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.dto.UserActivityReportVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default (and only) adapter for {@link UserActivityAnalyticsProjection}.
 * Owns the user + submission read joins that feed the admin activity report.
 *
 * <p>Previous N+1 issues documented on the old
 * {@code AdminUserAnalyticsServiceImpl} are preserved as-is in the move:
 * the daily/weekly/hourly buckets, retention window counts, and top-active-user
 * lookup are all single aggregation queries
 * ({@code countDailyActiveUsers}, {@code countWeeklyActiveUsers},
 * {@code countActiveUsersByHour}, {@code findTopActiveUsers},
 * {@code countDistinctUsersInRange}) — the N+1 paths no longer exist on the
 * hot path. Top-active-user username enrichment is a single batch RPC
 * via the public Auth {@code IdentityQueryService#batchGetIdentity} seam
 * (outer {@code findTopActiveUsers(startDate, 10)} caps the batch at 10
 * ids); an Auth provider outage degrades to "Unknown" usernames rather
 * than failing the whole report. {@code lastActive} comes from the
 * App-owned submission aggregation window, while Auth only supplies usernames.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultUserActivityAnalyticsProjection implements UserActivityAnalyticsProjection {

    private final Clock clock;

    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private IdentityQueryService identityQueryService;

    private final SubmissionActivityAnalyticsPort submissionActivityAnalytics;

    @Override
    public UserActivityReportVO loadUserActivityReport(Integer days) {
        int daysToAnalyze = days != null && days > 0 ? days : 30;
        LocalDateTime startDate = LocalDateTime.now(clock).minusDays(daysToAnalyze);

        UserActivityReportVO report = new UserActivityReportVO();

        // Daily active users - based on submissions (more representative than audit_logs
        // which only records admin operations)
        List<UserActivityReportVO.DailyActiveUsers> dailyActiveUsers = new ArrayList<>();
        LocalDateTime overallStart = LocalDateTime.now(clock).minusDays(daysToAnalyze).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime overallEnd = LocalDateTime.now(clock).plusDays(1).withHour(0).withMinute(0).withSecond(0);
        List<DailyActiveUserCount> dailyCounts = submissionActivityAnalytics.countDailyActiveUsers(overallStart, overallEnd);
        for (DailyActiveUserCount row : dailyCounts) {
            dailyActiveUsers.add(new UserActivityReportVO.DailyActiveUsers(
                    row.getDate(),
                    row.getCount() != null ? row.getCount().intValue() : 0
            ));
        }
        report.setActiveUsersDaily(dailyActiveUsers);

        // Weekly active users - single aggregation query replacing per-week N+1 loop
        List<UserActivityReportVO.DailyActiveUsers> weeklyActiveUsers = new ArrayList<>();
        List<WeeklyActiveUserCount> weeklyCounts = submissionActivityAnalytics.countWeeklyActiveUsers(startDate);
        for (WeeklyActiveUserCount row : weeklyCounts) {
            String weekStart = row.getWeekStart() != null
                    ? row.getWeekStart()
                    : String.valueOf(row.getYearWeek());
            int count = row.getCount() != null ? row.getCount().intValue() : 0;
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
        List<HourlyActiveUserCount> hourCounts = submissionActivityAnalytics.countActiveUsersByHour(LocalDateTime.now(clock).minusDays(30));
        for (HourlyActiveUserCount row : hourCounts) {
            int hour = row.getHour() != null ? row.getHour() : 0;
            int count = row.getCount() != null ? row.getCount().intValue() : 0;
            peakHours.add(new UserActivityReportVO.PeakActiveHour(hour, count));
        }
        peakHours.sort((a, b) -> Long.compare(b.getCount(), a.getCount()));
        report.setPeakActiveHours(peakHours.stream().limit(24).collect(Collectors.toList()));

        // Top active users - single aggregation query replacing load-all + Java groupBy + N user lookups
        List<UserActivityReportVO.TopActiveUser> topUsers = new ArrayList<>();
        List<TopActiveUserCount> topUserCounts = submissionActivityAnalytics.findTopActiveUsers(startDate, 10);
        if (!topUserCounts.isEmpty()) {
            // One batch RPC via the public Auth identity seam (bounded at 10 ids);
            // lastActive comes from the App-owned submission aggregation, not Auth identity.
            Map<String, String> usernamesById = new HashMap<>();
            if (identityQueryService != null) {
                Set<String> userIds = topUserCounts.stream()
                        .map(TopActiveUserCount::getUserId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                RpcResult<List<UserIdentityDTO>> identities = null;
                try {
                    identities = identityQueryService.batchGetIdentity(userIds);
                } catch (RuntimeException e) {
                    log.warn("IdentityQueryService.batchGetIdentity failed for {} ids: {}", userIds.size(), e.getMessage());
                }
                if (identities != null && identities.success() && identities.data() != null) {
                    for (UserIdentityDTO identity : identities.data()) {
                        if (identity != null && identity.accountId() != null) {
                            usernamesById.put(identity.accountId(), identity.username());
                        }
                    }
                }
            }
            for (TopActiveUserCount row : topUserCounts) {
                String userId = row.getUserId();
                int count = row.getSubmissionCount() != null ? row.getSubmissionCount().intValue() : 0;
                String username = userId != null ? usernamesById.getOrDefault(userId, "Unknown") : "Unknown";
                topUsers.add(new UserActivityReportVO.TopActiveUser(userId, username, count, row.getLastActive()));
            }
        }
        report.setTopActiveUsers(topUsers);

        // Average session duration (default value)
        report.setAverageSessionDuration(300.0); // 5 minutes default

        return report;
    }

    /**
     * Calculate retention rate for a given day.
     * Uses COUNT(DISTINCT user_id) aggregation queries for accurate distinct user counting.
     * Previous implementation used selectCount with groupBy which returns the count of the
     * first group only, not the total distinct user count (MyBatis-Plus Pitfall 4).
     *
     * <p>NOTE: This is an approximation using distinct user counts rather than
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

        long day0DistinctUsers = submissionActivityAnalytics.countDistinctUsersInRange(day0Start, day0End);

        if (day0DistinctUsers == 0) {
            return 0.0;
        }

        long dayNDistinctUsers = submissionActivityAnalytics.countDistinctUsersInRange(dayNStart, dayNEnd);

        // Approximate retention: ratio of distinct active users on day N vs day 0
        return Math.min(dayNDistinctUsers * 100.0 / day0DistinctUsers, 100.0);
    }
}
