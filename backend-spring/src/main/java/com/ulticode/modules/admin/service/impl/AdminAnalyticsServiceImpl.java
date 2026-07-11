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

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

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
 *   <li>delegates the JVM/OS performance report to
 *       {@link SystemResourceReporter},</li>
 *   <li>composes the lightweight analytics overview inline because it
 *       is a direct fan-out of small port queries plus a one-line heap
 *       sample (lifting it into a fourth reporter would add ceremony
 *       without deepening the seam).</li>
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
    public Map<String, Object> getAnalyticsOverview(Integer days) {
        int daysToAnalyze = days != null && days > 0 ? days : 30;
        LocalDateTime startDate = LocalDateTime.now(clock).minusDays(daysToAnalyze);

        Map<String, Object> overview = new LinkedHashMap<>();

        // User metrics
        long totalUsers = adminAnalyticsPort.countAllUsers();
        long activeUsers = adminAnalyticsPort.countDistinctSubmittersInRange(
                startDate, LocalDateTime.now(clock).plusDays(1));
        overview.put("totalUsers", totalUsers);
        overview.put("activeUsers", activeUsers);

        // Submission metrics
        long totalSubmissions = adminAnalyticsPort.countSubmissionsInRange(startDate);
        long acceptedSubmissions = adminAnalyticsPort.countAcceptedSubmissionsInRange(startDate);

        overview.put("totalSubmissions", totalSubmissions);
        overview.put("acceptedSubmissions", acceptedSubmissions);
        overview.put("acceptanceRate", totalSubmissions > 0
                ? Math.round(acceptedSubmissions * 100.0 / totalSubmissions * 100.0) / 100.0 : 0.0);

        // Contest metrics
        long totalContests = adminAnalyticsPort.countContestsInRange(startDate);
        overview.put("totalContests", totalContests);

        // Subscription metrics
        long activeSubscriptions = adminAnalyticsPort.countActiveSubscriptions();
        overview.put("activeSubscriptions", activeSubscriptions);

        // System metrics
        long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        overview.put("systemUptimeSeconds", uptimeSeconds);

        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        long maxMemory = heapUsage.getMax() > 0 ? heapUsage.getMax() : 1;
        overview.put("memoryUsagePercent", Math.round(heapUsage.getUsed() * 10000.0 / maxMemory) / 100.0);

        overview.put("periodDays", daysToAnalyze);

        return overview;
    }
}