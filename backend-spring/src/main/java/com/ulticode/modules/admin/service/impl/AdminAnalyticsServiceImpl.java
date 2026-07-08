package com.ulticode.modules.admin.service.impl;

import com.sun.management.OperatingSystemMXBean;
import com.ulticode.modules.admin.dto.*;
import com.ulticode.modules.admin.port.AdminAnalyticsPort;
import com.ulticode.modules.admin.service.AdminAnalyticsService;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.problem.projection.ProblemAnalyticsProjection;
import com.ulticode.modules.subscription.entity.Subscription;
import com.ulticode.modules.user.projection.UserActivityAnalyticsProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Facade implementation of {@link AdminAnalyticsService}. Delegates the
 * user-activity and problem-completion reports to read-side projections
 * owned by the user and problem modules respectively
 * ({@link UserActivityAnalyticsProjection},
 * {@link ProblemAnalyticsProjection}). Retains the contest participation
 * and revenue logic directly, and inlines the lightweight JVM resource
 * report (previously a separate half-stubbed service) so a single
 * facade controls the analytics surface.
 *
 * <p>Cross-module reads (contest / participant / subscription / submission /
 * user / problem) live behind {@link AdminAnalyticsPort}; the five mappers
 * that used to be imported here are no longer dependencies. The two
 * projections carry the user + problem deep joins; this facade carries
 * only the contest + subscription + revenue math and the JVM-internal
 * resource read.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAnalyticsServiceImpl implements AdminAnalyticsService {

    private final UserActivityAnalyticsProjection userActivityAnalyticsProjection;
    private final ProblemAnalyticsProjection problemAnalyticsProjection;

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
        int daysToAnalyze = days != null && days > 0 ? days : 30;
        LocalDateTime startDate = LocalDateTime.now(clock).minusDays(daysToAnalyze);

        LoadedContestData data = loadContestData(startDate);
        List<Contest> contests = data.contests();
        Map<String, Long> participantsByContest = data.participantsByContest();
        Set<String> uniqueParticipants = data.uniqueParticipants();

        ContestParticipationReportVO report = new ContestParticipationReportVO();
        int totalContestsCount = contests.size();
        report.setTotalContests(totalContestsCount);
        report.setTotalParticipants((long) uniqueParticipants.size());
        report.setAverageParticipantsPerContest(
                totalContestsCount > 0 ? (double) uniqueParticipants.size() / totalContestsCount : 0.0);
        report.setByType(buildTypeStats(contests, participantsByContest));
        report.setTopContests(buildTopContests(contests, participantsByContest));
        report.setVirtualParticipation(new ContestParticipationReportVO.VirtualParticipation(0, 0.0));
        report.setParticipationTrend(buildParticipationTrend(contests, participantsByContest, daysToAnalyze));

        return report;
    }

    /**
     * Internal struct returned by {@link #loadContestData(LocalDateTime)} so the
     * public method can stay small and delegate the multi-step data loading.
     */
    private record LoadedContestData(
            List<Contest> contests,
            Map<String, Long> participantsByContest,
            Set<String> uniqueParticipants
    ) {}

    /**
     * Load all contests in the period and a single batch of their participants
     * (replaces the previous per-contest N+1 loop with one query each).
     */
    private LoadedContestData loadContestData(LocalDateTime startDate) {
        AdminAnalyticsPort.ContestParticipationData data = adminAnalyticsPort.loadContestData(startDate);
        return new LoadedContestData(data.contests(), data.participantsByContest(), data.uniqueParticipants());
    }

    /**
     * Group contests by {@code contestType} and compute the running average
     * participants per contest in each type bucket.
     */
    private List<ContestParticipationReportVO.TypeStats> buildTypeStats(
            List<Contest> contests, Map<String, Long> participantsByContest) {
        Map<String, ContestParticipationReportVO.TypeStats> typeStatsMap = new HashMap<>();
        for (Contest contest : contests) {
            long participantCount = participantsByContest.getOrDefault(contest.getId(), 0L);
            typeStatsMap.merge(contest.getContestType(),
                    new ContestParticipationReportVO.TypeStats(contest.getContestType(), 1, (double) participantCount),
                    (existing, newValue) -> new ContestParticipationReportVO.TypeStats(
                            contest.getContestType(),
                            existing.getCount() + 1,
                            (existing.getAvgParticipants() * existing.getCount() + participantCount) / (existing.getCount() + 1)
                    ));
        }
        return new ArrayList<>(typeStatsMap.values());
    }

    /**
     * Build the top-N (currently 10) contests by participant count.
     */
    private List<ContestParticipationReportVO.TopContest> buildTopContests(
            List<Contest> contests, Map<String, Long> participantsByContest) {
        return contests.stream()
                .map(contest -> new ContestParticipationReportVO.TopContest(
                        contest.getId(),
                        contest.getTitle(),
                        participantsByContest.getOrDefault(contest.getId(), 0L).intValue(),
                        100.0 // Default completion rate
                ))
                .sorted((a, b) -> Integer.compare(b.getParticipants(), a.getParticipants()))
                .limit(10)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Bucket the already-loaded contests into weeks (oldest first) and produce
     * one trend row per week. The {@code participants} field is the sum of
     * per-contest participation counts within the week — see
     * {@link ContestParticipationReportVO.ParticipationTrend} for why this is
     * an approximation rather than a distinct-user count.
     */
    private List<ContestParticipationReportVO.ParticipationTrend> buildParticipationTrend(
            List<Contest> contests, Map<String, Long> participantsByContest, int daysToAnalyze) {
        List<ContestParticipationReportVO.ParticipationTrend> trend = new ArrayList<>();
        for (int i = (daysToAnalyze / 7); i >= 0; i--) {
            LocalDateTime weekStart = LocalDateTime.now(clock).minusWeeks(i).withHour(0).withMinute(0).withSecond(0);
            LocalDateTime weekEnd = weekStart.plusWeeks(1);

            List<Contest> weekContests = contests.stream()
                    .filter(c -> !c.getStartTime().isBefore(weekStart) && c.getStartTime().isBefore(weekEnd))
                    .collect(java.util.stream.Collectors.toList());

            long weekParticipants = weekContests.stream()
                    .mapToLong(c -> participantsByContest.getOrDefault(c.getId(), 0L))
                    .sum();

            trend.add(new ContestParticipationReportVO.ParticipationTrend(
                    weekStart.toLocalDate().toString(),
                    weekContests.size(),
                    (int) weekParticipants
            ));
        }
        return trend;
    }

    @Override
    public RevenueReportVO getRevenueReport(Integer days) {
        int daysToAnalyze = days != null && days > 0 ? days : 30;
        LocalDateTime startDate = LocalDateTime.now(clock).minusDays(daysToAnalyze);

        RevenueReportVO report = new RevenueReportVO();

        // Active subscriptions come from the analytics port.
        List<Subscription> activeSubscriptions = adminAnalyticsPort.listActiveSubscriptions();

        Map<String, RevenueReportVO.PlanRevenue> planRevenueMap = new HashMap<>();
        for (Subscription sub : activeSubscriptions) {
            String plan = sub.getPlan();
            double monthlyRevenue = estimateMonthlyRevenue(plan);
            planRevenueMap.merge(plan,
                    new RevenueReportVO.PlanRevenue(plan, 1, monthlyRevenue),
                    (existing, newValue) -> new RevenueReportVO.PlanRevenue(
                            plan,
                            existing.getSubscribers() + 1,
                            existing.getRevenue() + monthlyRevenue
                    )
            );
        }

        report.setByPlan(new ArrayList<>(planRevenueMap.values()));

        // Calculate MRR and ARR
        double mrr = planRevenueMap.values().stream()
                .mapToDouble(RevenueReportVO.PlanRevenue::getRevenue)
                .sum();
        report.setMrr(mrr);
        report.setArr(mrr * 12);

        // Subscriber count
        report.setSubscriberCount(activeSubscriptions.size());

        // ARPU
        double arpu = activeSubscriptions.size() > 0 ? mrr / activeSubscriptions.size() : 0.0;
        report.setArpu(arpu);

        // Total revenue in period
        report.setTotalRevenue(mrr * (daysToAnalyze / 30.0));

        // Placeholder values
        report.setChurnRate(5.0); // Default 5% churn rate
        report.setConversionRate(2.5); // Default 2.5% conversion rate

        // Revenue trend (simplified)
        List<RevenueReportVO.RevenueTrend> revenueTrend = new ArrayList<>();
        for (int i = Math.min(daysToAnalyze, 30) - 1; i >= 0; i--) {
            LocalDateTime dayStart = LocalDateTime.now(clock).minusDays(i).withHour(0).withMinute(0).withSecond(0);
            revenueTrend.add(new RevenueReportVO.RevenueTrend(
                    dayStart.toLocalDate().toString(),
                    mrr / 30, // Daily revenue approximation
                    0, // New subscribers (placeholder)
                    0  // Churned (placeholder)
            ));
        }
        revenueTrend.sort(Comparator.comparing(RevenueReportVO.RevenueTrend::getDate));
        report.setRevenueTrend(revenueTrend);

        return report;
    }

    @Override
    public PerformanceReportVO getPerformanceReport() {
        PerformanceReportVO report = new PerformanceReportVO();

        // System uptime (JVM uptime)
        long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
        report.setSystemUptime(uptimeMillis / 1000);

        // Resource usage
        PerformanceReportVO.ResourceUsage resourceUsage = new PerformanceReportVO.ResourceUsage();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        long maxMemory = heapUsage.getMax() > 0 ? heapUsage.getMax() : 1;
        double memoryUsagePercent = (heapUsage.getUsed() * 100.0) / maxMemory;
        resourceUsage.setCpu(readSystemCpuLoad());
        resourceUsage.setMemory(Math.round(memoryUsagePercent * 100.0) / 100.0);
        resourceUsage.setDisk(readRootDiskUsagePercent());
        report.setResourceUsage(resourceUsage);

        // Performance metrics - these require external monitoring (APM tool).
        // Sentinel -1.0/-1L values were dropped in favor of null + @JsonInclude(NON_NULL).
        report.setAverageResponseTime(null);
        report.setErrorRate(null);
        report.setThroughput(0L);
        report.setCacheHitRate(null);

        // Slowest endpoints - requires request timing middleware
        report.setSlowestEndpoints(Collections.emptyList());

        // Error breakdown - requires error tracking integration
        report.setErrorBreakdown(Collections.emptyList());

        return report;
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

    // ==================== Private Helper Methods ====================

    /**
     * Estimate monthly revenue for a subscription plan.
     */
    private double estimateMonthlyRevenue(String plan) {
        return switch (plan) {
            case "PREMIUM_MONTHLY" -> 9.99;
            case "PREMIUM_YEARLY" -> 79.99 / 12;
            default -> 0.0;
        };
    }

    /**
     * Read recent system-wide CPU load (0.0–100.0).
     * Returns {@code -1.0} if the JVM has not yet collected enough samples
     * (the first call to {@link OperatingSystemMXBean#getSystemCpuLoad()}
     * typically returns -1) or if the value cannot be obtained on this JVM.
     */
    private double readSystemCpuLoad() {
        try {
            OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            double load = osBean.getSystemCpuLoad();
            if (load < 0) {
                return -1.0;
            }
            return Math.round(load * 10000.0) / 100.0;
        } catch (Exception e) {
            log.warn("Failed to read system CPU load", e);
            return -1.0;
        }
    }

    /**
     * Read root filesystem usage as a percentage (0.0–100.0).
     * Returns {@code -1.0} if the underlying OS does not report filesystem stats.
     */
    private double readRootDiskUsagePercent() {
        try {
            File root = new File("/");
            long total = root.getTotalSpace();
            if (total <= 0) {
                return -1.0;
            }
            long used = total - root.getFreeSpace();
            return Math.round(used * 10000.0 / total) / 100.0;
        } catch (Exception e) {
            log.warn("Failed to read root disk usage", e);
            return -1.0;
        }
    }
}
