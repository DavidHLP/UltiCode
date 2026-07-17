package com.ulticode.modules.admin.service.impl;

import com.ulticode.modules.admin.analytics.ContestParticipationReporter;
import com.ulticode.modules.admin.analytics.RevenueReporter;
import com.ulticode.modules.admin.analytics.SystemResourceReporter;
import com.ulticode.modules.admin.dto.*;
import com.ulticode.modules.admin.port.AdminAnalyticsPort;
import com.ulticode.modules.admin.service.AdminAnalyticsService;
import com.ulticode.modules.problem.projection.ProblemAnalyticsProjection;
import com.ulticode.modules.user.projection.UserActivityAnalyticsProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Facade implementation of {@link AdminAnalyticsService}. A thin
 * orchestrator that:
 *
 * <ul>
 *   <li>delegates user-activity and problem-completion reports to the
 *       read-side projections owned by their respective modules
 *       ({@link UserActivityAnalyticsProjection},
 *       {@link ProblemAnalyticsProjection}),</li>
 *   <li>delegates the contest-participation report to
 *       {@link ContestParticipationReporter},</li>
 *   <li>delegates the revenue report to {@link RevenueReporter},</li>
 *   <li>delegates the JVM/OS performance report and the platform-overview
 *       system samples to {@link SystemResourceReporter},</li>
 *   <li>composes the lightweight analytics overview by fan-out of port
 *       queries plus the two system samples; the foreign entity imports
 *       the pre-refactor service carried are gone — the port now returns
 *       admin-owned projection records.</li>
 * </ul>
 *
 * <p>Cross-module reads still flow through {@link AdminAnalyticsPort};
 * the foreign {@code Contest} / {@code Subscription} entity imports
 * the pre-refactor service carried are gone — the port now returns
 * admin-owned projection records.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAnalyticsServiceImpl implements AdminAnalyticsService {

    private final UserActivityAnalyticsProjection userActivityAnalyticsProjection;
    private final ProblemAnalyticsProjection problemAnalyticsProjection;

    private final ContestParticipationReporter contestParticipationReporter;
    private final RevenueReporter revenueReporter;
    private final SystemResourceReporter systemResourceReporter;

    private final AdminAnalyticsPort adminAnalyticsPort;
    private final Clock clock;

    @Override
    public UserActivityReportVO getUserActivityReport(Integer days) {
        return userActivityAnalyticsProjection.loadUserActivityReport(days);
    }

    @Override
    public ProblemCompletionReportVO getProblemCompletionReport(Integer days) {
        return problemAnalyticsProjection.loadProblemCompletionReport(days);
    }

    @Override
    public ContestParticipationReportVO getContestParticipationReport(Integer days) {
        return contestParticipationReporter.buildReport(days);
    }

    @Override
    public RevenueReportVO getRevenueReport(Integer days) {
        return revenueReporter.buildReport(days);
    }

    @Override
    public PerformanceReportVO getPerformanceReport() {
        return systemResourceReporter.buildReport();
    }

    @Override
    public AnalyticsOverviewVO getAnalyticsOverview(Integer days) {
        int daysToAnalyze = days != null && days > 0 ? days : 30;
        LocalDateTime startDate = LocalDateTime.now(clock).minusDays(daysToAnalyze);

        // User metrics
        long totalUsers = adminAnalyticsPort.countAllUsers();
        long activeUsers = adminAnalyticsPort.countDistinctSubmittersInRange(
                startDate, LocalDateTime.now(clock).plusDays(1));

        // Submission metrics
        long totalSubmissions = adminAnalyticsPort.countSubmissionsInRange(startDate);
        long acceptedSubmissions = adminAnalyticsPort.countAcceptedSubmissionsInRange(startDate);
        double acceptanceRate = totalSubmissions > 0
                ? Math.round(acceptedSubmissions * 100.0 / totalSubmissions * 100.0) / 100.0 : 0.0;

        // Contest metrics
        long totalContests = adminAnalyticsPort.countContestsInRange(startDate);

        // Subscription metrics
        long activeSubscriptions = adminAnalyticsPort.countActiveSubscriptions();

        // System metrics (JVM/OS sampling lives in SystemResourceReporter)
        SystemResourceReporter.SystemMetrics systemMetrics = systemResourceReporter.sampleSystemMetrics();

        return new AnalyticsOverviewVO(
                totalUsers,
                activeUsers,
                totalSubmissions,
                acceptedSubmissions,
                acceptanceRate,
                totalContests,
                activeSubscriptions,
                systemMetrics.systemUptimeSeconds(),
                systemMetrics.memoryUsagePercent(),
                daysToAnalyze);
    }
}