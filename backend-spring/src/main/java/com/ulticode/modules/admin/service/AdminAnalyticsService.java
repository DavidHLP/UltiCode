package com.ulticode.modules.admin.service;

import com.ulticode.modules.admin.dto.*;

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
     *
     * @param days number of days to analyze (default: 30)
     * @return typed overview VO with user, submission, contest, subscription, and
     *         system metrics for the resolved period
     */
    AnalyticsOverviewVO getAnalyticsOverview(Integer days);
}
