package com.ulticode.submission.api.service;

import com.ulticode.submission.api.dto.DailyActiveUserCount;
import com.ulticode.submission.api.dto.HourlyActiveUserCount;
import com.ulticode.submission.api.dto.TopActiveUserCount;
import com.ulticode.submission.api.dto.WeeklyActiveUserCount;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Read port for submission-derived active-user analytics.
 */
public interface SubmissionActivityAnalyticsPort {

    /** Distinct submitters per day within {@code [startDate, endDate]}. */
    List<DailyActiveUserCount> countDailyActiveUsers(LocalDateTime startDate, LocalDateTime endDate);

    /** Weekly active-user counts from {@code startDate}. */
    List<WeeklyActiveUserCount> countWeeklyActiveUsers(LocalDateTime startDate);

    /** Active-user counts bucketed by hour of day from {@code startDate}. */
    List<HourlyActiveUserCount> countActiveUsersByHour(LocalDateTime startDate);

    /** Top submitters (user_id, submission_count) from {@code startDate}. */
    List<TopActiveUserCount> findTopActiveUsers(LocalDateTime startDate, int limit);

    /** Count of distinct users who submitted within {@code [startDate, endDate]}. */
    Long countActiveUsersBetween(LocalDateTime startDate, LocalDateTime endDate);

    /** Count distinct users who submitted within [startDate, endDate]. Alias for countActiveUsersBetween. */
    default long countDistinctUsersInRange(LocalDateTime startDate, LocalDateTime endDate) {
        return countActiveUsersBetween(startDate, endDate);
    }
}
