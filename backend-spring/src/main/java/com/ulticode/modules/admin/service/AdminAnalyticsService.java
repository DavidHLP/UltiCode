package com.ulticode.modules.admin.service;

import com.ulticode.modules.admin.dto.*;

import java.util.Map;

/**
 * Service interface for admin analytics operations.
 * Provides aggregated data for various analytics reports.
 */
public interface AdminAnalyticsService {

    /**
     * Get user activity report.
     *
     * @param days number of days to analyze (default: 30)
     * @return user activity report with daily active users, retention, etc.
     */
    UserActivityReportVO getUserActivityReport(Integer days);

    /**
     * Get problem completion report.
     *
     * @param days number of days to analyze (default: 30)
     * @return problem completion report with statistics by difficulty and tags
     */
    ProblemCompletionReportVO getProblemCompletionReport(Integer days);

    /**
     * Get contest participation report.
     *
     * @param days number of days to analyze (default: 30)
     * @return contest participation report with trends and top contests
     */
    ContestParticipationReportVO getContestParticipationReport(Integer days);

    /**
     * Get revenue report.
     *
     * @param days number of days to analyze (default: 30)
     * @return revenue report with MRR, ARR, subscriber counts, etc.
     */
    RevenueReportVO getRevenueReport(Integer days);

    /**
     * Get performance report.
     *
     * @return performance report with system metrics and resource usage
     */
    PerformanceReportVO getPerformanceReport();

    /**
     * Get analytics overview with key metrics from all report types.
     * Returns a lightweight summary without the full detail of individual reports.
     * <p>
     * The returned map contains the following keys:
     * <ul>
     *   <li>{@code totalUsers} (Long) — total registered users</li>
     *   <li>{@code activeUsers} (Long) — distinct users with submissions in the period</li>
     *   <li>{@code totalSubmissions} (Long) — total submissions in the period</li>
     *   <li>{@code acceptedSubmissions} (Long) — accepted submissions in the period</li>
     *   <li>{@code acceptanceRate} (Double) — percentage 0–100, 2 decimals</li>
     *   <li>{@code totalContests} (Long) — contests started in the period</li>
     *   <li>{@code activeSubscriptions} (Long) — subscriptions with status ACTIVE</li>
     *   <li>{@code systemUptimeSeconds} (Long) — JVM uptime in seconds</li>
     *   <li>{@code memoryUsagePercent} (Double) — JVM heap usage, 0–100, 2 decimals</li>
     *   <li>{@code periodDays} (Integer) — echo of the resolved {@code days} parameter</li>
     * </ul>
     * <p>
     * <b>Why {@code Map<String, Object>}?</b> Deliberately untyped to keep this
     * endpoint flexible during the API surface iteration. If the contract
     * stabilizes, migrate to a dedicated {@code AnalyticsOverviewVO} for type safety.
     *
     * @param days number of days to analyze (default: 30)
     * @return map of key metric names to values (see key list above)
     */
    Map<String, Object> getAnalyticsOverview(Integer days);
}
