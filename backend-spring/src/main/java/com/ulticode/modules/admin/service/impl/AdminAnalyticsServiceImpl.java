package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.admin.dto.*;
import com.ulticode.modules.admin.service.AdminAnalyticsService;
import com.ulticode.modules.admin.service.AdminContentAnalyticsService;
import com.ulticode.modules.admin.service.AdminPerformanceReportService;
import com.ulticode.modules.admin.service.AdminUserAnalyticsService;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.subscription.entity.Subscription;
import com.ulticode.modules.subscription.mapper.SubscriptionMapper;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Facade implementation of AdminAnalyticsService.
 * Delegates to focused services for user analytics, content analytics, and performance reporting.
 * Retains contest participation and revenue logic directly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAnalyticsServiceImpl implements AdminAnalyticsService {

    private final AdminUserAnalyticsService userAnalyticsService;
    private final AdminContentAnalyticsService contentAnalyticsService;
    private final AdminPerformanceReportService performanceReportService;

    private final ContestMapper contestMapper;
    private final ContestParticipantMapper contestParticipantMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final SubmissionMapper submissionMapper;
    private final UserMapper userMapper;
    private final ProblemMapper problemMapper;
    private final Clock clock;

    @Override
    public UserActivityReportVO getUserActivityReport(Integer days) {
        return userAnalyticsService.getUserActivityReport(days);
    }

    @Override
    public ProblemCompletionReportVO getProblemCompletionReport(Integer days) {
        return contentAnalyticsService.getProblemCompletionReport(days);
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
        LambdaQueryWrapper<Contest> contestWrapper = new LambdaQueryWrapper<>();
        contestWrapper.ge(Contest::getStartTime, startDate);
        List<Contest> contests = contestMapper.selectList(contestWrapper);

        Map<String, Long> participantsByContest = new HashMap<>();
        Set<String> uniqueParticipants = new HashSet<>();
        if (!contests.isEmpty()) {
            // Short-circuit avoids IN () syntax error in MySQL.
            List<String> contestIds = contests.stream().map(Contest::getId).collect(Collectors.toList());
            for (ContestParticipant p : contestParticipantMapper.findByContestIds(contestIds)) {
                participantsByContest.merge(p.getContestId(), 1L, Long::sum);
                uniqueParticipants.add(p.getUserId());
            }
        }
        return new LoadedContestData(contests, participantsByContest, uniqueParticipants);
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
                .collect(Collectors.toList());
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
                    .collect(Collectors.toList());

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

        // Active subscriptions by plan
        LambdaQueryWrapper<Subscription> activeWrapper = new LambdaQueryWrapper<>();
        activeWrapper.eq(Subscription::getStatus, "ACTIVE");

        List<Subscription> activeSubscriptions = subscriptionMapper.selectList(activeWrapper);

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
        return performanceReportService.getPerformanceReport();
    }

    @Override
    public Map<String, Object> getAnalyticsOverview(Integer days) {
        int daysToAnalyze = days != null && days > 0 ? days : 30;
        LocalDateTime startDate = LocalDateTime.now(clock).minusDays(daysToAnalyze);

        Map<String, Object> overview = new LinkedHashMap<>();

        // User metrics
        long totalUsers = userMapper.selectCount(null);
        long activeUsers = submissionMapper.countDistinctUsersInRange(
                startDate, LocalDateTime.now(clock).plusDays(1));
        overview.put("totalUsers", totalUsers);
        overview.put("activeUsers", activeUsers);

        // Submission metrics
        LambdaQueryWrapper<Submission> subWrapper = new LambdaQueryWrapper<>();
        subWrapper.ge(Submission::getCreatedAt, startDate);
        long totalSubmissions = submissionMapper.selectCount(subWrapper);

        LambdaQueryWrapper<Submission> acceptedWrapper = new LambdaQueryWrapper<>();
        acceptedWrapper.ge(Submission::getCreatedAt, startDate)
                .eq(Submission::getStatus, "Accepted");
        long acceptedSubmissions = submissionMapper.selectCount(acceptedWrapper);

        overview.put("totalSubmissions", totalSubmissions);
        overview.put("acceptedSubmissions", acceptedSubmissions);
        overview.put("acceptanceRate", totalSubmissions > 0
                ? Math.round(acceptedSubmissions * 100.0 / totalSubmissions * 100.0) / 100.0 : 0.0);

        // Contest metrics
        LambdaQueryWrapper<Contest> contestWrapper = new LambdaQueryWrapper<>();
        contestWrapper.ge(Contest::getStartTime, startDate);
        long totalContests = contestMapper.selectCount(contestWrapper);
        overview.put("totalContests", totalContests);

        // Subscription metrics
        LambdaQueryWrapper<Subscription> activeSubWrapper = new LambdaQueryWrapper<>();
        activeSubWrapper.eq(Subscription::getStatus, "ACTIVE");
        long activeSubscriptions = subscriptionMapper.selectCount(activeSubWrapper);
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
}
