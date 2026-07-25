package com.ulticode.modules.submission.port;

import com.ulticode.modules.submission.dto.DailyActiveUserCount;
import com.ulticode.modules.submission.dto.HourlyActiveUserCount;
import com.ulticode.modules.submission.dto.TopActiveUserCount;
import com.ulticode.modules.submission.dto.WeeklyActiveUserCount;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Read port for submission-derived active-user analytics.
 *
 * <p>The submission module owns this port; the user activity analytics
 * projection depends on it rather than importing {@code SubmissionMapper}.
 * Each aggregation returns a typed row DTO so the analytics contract is
 * explicit at the seam (mirroring {@code SubmissionUserStatsPort}).
 */
public interface SubmissionActivityAnalyticsPort {

    /** Distinct submitters per day within {@code [startDate, endDate)}. */
    List<DailyActiveUserCount> countDailyActiveUsers(LocalDateTime startDate, LocalDateTime endDate);

    /** Weekly active-user counts from {@code startDate}. */
    List<WeeklyActiveUserCount> countWeeklyActiveUsers(LocalDateTime startDate);

    /** Active-user counts bucketed by hour of day from {@code startDate}. */
    List<HourlyActiveUserCount> countActiveUsersByHour(LocalDateTime startDate);

    /** Top submitters (user_id, submission_count) from {@code startDate}. */
    List<TopActiveUserCount> findTopActiveUsers(LocalDateTime startDate, int limit);

    /** Count of distinct users who submitted within {@code [startDate, endDate)}. */
    long countDistinctUsersInRange(LocalDateTime startDate, LocalDateTime endDate);
}
