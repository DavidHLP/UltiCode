package com.ulticode.modules.submission.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Read port for submission-derived active-user analytics.
 *
 * <p>The submission module owns this port; the user activity analytics
 * projection depends on it rather than importing {@code SubmissionMapper}.
 * The row shapes are the mapper's existing aggregation result maps, kept as
 * {@code Map<String, Object>} to preserve the current analytics contract
 * (retyping is out of scope for this seam).
 */
public interface SubmissionActivityAnalyticsPort {

    /** Distinct submitters per day within {@code [startDate, endDate)}. */
    List<Map<String, Object>> countDailyActiveUsers(LocalDateTime startDate, LocalDateTime endDate);

    /** Weekly active-user counts from {@code startDate}. */
    List<Map<String, Object>> countWeeklyActiveUsers(LocalDateTime startDate);

    /** Active-user counts bucketed by hour of day from {@code startDate}. */
    List<Map<String, Object>> countActiveUsersByHour(LocalDateTime startDate);

    /** Top submitters (user_id, submission_count) from {@code startDate}. */
    List<Map<String, Object>> findTopActiveUsers(LocalDateTime startDate, int limit);

    /** Count of distinct users who submitted within {@code [startDate, endDate)}. */
    long countDistinctUsersInRange(LocalDateTime startDate, LocalDateTime endDate);
}
