package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.admin.dto.*;
import com.ulticode.modules.admin.mapper.AuditLogMapper;
import com.ulticode.modules.admin.service.AdminAnalyticsService;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.entity.ProblemTag;
import com.ulticode.modules.problem.entity.ProblemTagRelation;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.mapper.ProblemTagMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.subscription.entity.Subscription;
import com.ulticode.modules.subscription.mapper.SubscriptionMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of AdminAnalyticsService.
 * Provides aggregated analytics data for admin dashboard.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAnalyticsServiceImpl implements AdminAnalyticsService {

    private final UserMapper userMapper;
    private final SubmissionMapper submissionMapper;
    private final ContestMapper contestMapper;
    private final ContestParticipantMapper contestParticipantMapper;
    private final ProblemMapper problemMapper;
    private final ProblemTagMapper problemTagMapper;
    private final ProblemTagRelationMapper problemTagRelationMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final AuditLogMapper auditLogMapper;

    @Override
    public UserActivityReportVO getUserActivityReport(Integer days) {
        int daysToAnalyze = days != null && days > 0 ? days : 30;
        LocalDateTime startDate = LocalDateTime.now().minusDays(daysToAnalyze);

        UserActivityReportVO report = new UserActivityReportVO();

        // Daily active users - single aggregated query replacing per-day N+1 loop
        List<UserActivityReportVO.DailyActiveUsers> dailyActiveUsers = new ArrayList<>();
        LocalDateTime overallStart = LocalDateTime.now().minusDays(daysToAnalyze).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime overallEnd = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0);
        List<Map<String, Object>> dailyCounts = auditLogMapper.countDailyActiveUsers(overallStart, overallEnd);
        for (Map<String, Object> row : dailyCounts) {
            dailyActiveUsers.add(new UserActivityReportVO.DailyActiveUsers(
                    row.get("date").toString(),
                    ((Number) row.get("count")).intValue()
            ));
        }
        report.setActiveUsersDaily(dailyActiveUsers);

        // Weekly active users - single aggregation query replacing per-week N+1 loop
        List<UserActivityReportVO.DailyActiveUsers> weeklyActiveUsers = new ArrayList<>();
        List<Map<String, Object>> weeklyCounts = submissionMapper.countWeeklyActiveUsers(startDate);
        for (Map<String, Object> row : weeklyCounts) {
            String weekStart = row.get("week_start") != null
                    ? row.get("week_start").toString()
                    : row.get("yearweek").toString();
            int count = ((Number) row.get("count")).intValue();
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
        List<Map<String, Object>> hourCounts = submissionMapper.countActiveUsersByHour(LocalDateTime.now().minusDays(30));
        for (Map<String, Object> row : hourCounts) {
            int hour = ((Number) row.get("hour")).intValue();
            int count = ((Number) row.get("count")).intValue();
            peakHours.add(new UserActivityReportVO.PeakActiveHour(hour, count));
        }
        peakHours.sort((a, b) -> Long.compare(b.getCount(), a.getCount()));
        report.setPeakActiveHours(peakHours.stream().limit(24).collect(Collectors.toList()));

        // Top active users - single aggregation query replacing load-all + Java groupBy + N user lookups
        List<UserActivityReportVO.TopActiveUser> topUsers = new ArrayList<>();
        List<Map<String, Object>> topUserCounts = submissionMapper.findTopActiveUsers(startDate, 10);
        for (Map<String, Object> row : topUserCounts) {
            String userId = row.get("user_id").toString();
            int count = ((Number) row.get("submission_count")).intValue();
            User user = userMapper.selectById(userId);
            topUsers.add(new UserActivityReportVO.TopActiveUser(
                    userId,
                    user != null ? user.getUsername() : "Unknown",
                    count,
                    user != null ? user.getLastLoginAt() : null
            ));
        }
        report.setTopActiveUsers(topUsers);

        // Average session duration (default value)
        report.setAverageSessionDuration(300.0); // 5 minutes default

        return report;
    }

    @Override
    public ProblemCompletionReportVO getProblemCompletionReport(Integer days) {
        int daysToAnalyze = days != null && days > 0 ? days : 30;
        LocalDateTime startDate = LocalDateTime.now().minusDays(daysToAnalyze);

        ProblemCompletionReportVO report = new ProblemCompletionReportVO();

        // Total attempts
        LambdaQueryWrapper<Submission> allWrapper = new LambdaQueryWrapper<>();
        allWrapper.ge(Submission::getCreatedAt, startDate);
        long totalAttempts = submissionMapper.selectCount(allWrapper);
        report.setTotalAttempts(totalAttempts);

        // Successful attempts (Accepted status)
        LambdaQueryWrapper<Submission> acceptedWrapper = new LambdaQueryWrapper<>();
        acceptedWrapper.ge(Submission::getCreatedAt, startDate)
                .eq(Submission::getStatus, "Accepted");
        long successfulAttempts = submissionMapper.selectCount(acceptedWrapper);
        report.setSuccessfulAttempts(successfulAttempts);

        // Overall completion rate
        double overallRate = totalAttempts > 0 ? (successfulAttempts * 100.0 / totalAttempts) : 0.0;
        report.setOverallCompletionRate(overallRate);

        // By difficulty - single aggregation query replacing per-difficulty per-problem N+1 loop
        List<ProblemCompletionReportVO.DifficultyStats> byDifficulty = new ArrayList<>();
        List<Map<String, Object>> diffStats = submissionMapper.countProblemCompletionByDifficulty();
        Map<String, Map<String, Object>> diffMap = diffStats.stream()
                .collect(Collectors.toMap(row -> row.get("difficulty").toString(), row -> row));
        for (String difficulty : Arrays.asList("EASY", "MEDIUM", "HARD")) {
            Map<String, Object> stats = diffMap.get(difficulty);
            int totalProblems = stats != null ? ((Number) stats.get("total_problems")).intValue() : 0;
            int solvedProblems = stats != null ? ((Number) stats.get("solved_problems")).intValue() : 0;
            double rate = totalProblems > 0 ? (solvedProblems * 100.0 / totalProblems) : 0.0;
            byDifficulty.add(new ProblemCompletionReportVO.DifficultyStats(difficulty, totalProblems, solvedProblems, rate));
        }
        report.setByDifficulty(byDifficulty);

        // By tag (top 10)
        // NOTE: N+1 issue exists in the tag loop below (per-problem submission count queries).
        // The LIMIT 1000 caps the outer result set to prevent unbounded memory usage.
        // A future optimization should batch the per-problem queries into a single GROUP BY.
        List<ProblemTag> allTags = problemTagMapper.selectList(
                new LambdaQueryWrapper<ProblemTag>().last("LIMIT 1000"));
        List<ProblemCompletionReportVO.TagStats> byTag = allTags.stream()
                .limit(10)
                .map(tag -> {
                    List<ProblemTagRelation> relations = problemTagRelationMapper.selectList(
                            new LambdaQueryWrapper<ProblemTagRelation>()
                                    .eq(ProblemTagRelation::getTagId, tag.getId())
                    );

                    int totalProblems = relations.size();
                    int solvedProblems = 0;

                    for (ProblemTagRelation relation : relations) {
                        LambdaQueryWrapper<Submission> subWrapper = new LambdaQueryWrapper<>();
                        subWrapper.eq(Submission::getProblemId, relation.getProblemId())
                                .eq(Submission::getStatus, "Accepted");
                        if (submissionMapper.selectCount(subWrapper) > 0) {
                            solvedProblems++;
                        }
                    }

                    double rate = totalProblems > 0 ? (solvedProblems * 100.0 / totalProblems) : 0.0;
                    return new ProblemCompletionReportVO.TagStats(tag.getId(), tag.getLabel(), totalProblems, solvedProblems, rate);
                })
                .sorted((a, b) -> Double.compare(b.getRate(), a.getRate()))
                .collect(Collectors.toList());
        report.setByTag(byTag);

        // Trending problems - single aggregation query replacing load-all + Java groupBy + N lookups
        List<ProblemCompletionReportVO.TrendingProblem> trendingProblems = new ArrayList<>();
        List<Map<String, Object>> trendingData = submissionMapper.findTrendingProblems(startDate, 10);
        for (Map<String, Object> row : trendingData) {
            long problemId = ((Number) row.get("problem_id")).longValue();
            int attemptCount = ((Number) row.get("attempt_count")).intValue();
            int acceptedCount = ((Number) row.get("accepted_count")).intValue();
            double rate = attemptCount > 0 ? (acceptedCount * 100.0 / attemptCount) : 0.0;
            Problem problem = problemMapper.selectById(problemId);
            trendingProblems.add(new ProblemCompletionReportVO.TrendingProblem(
                    String.valueOf(problemId),
                    problem != null ? problem.getTitle() : "Problem " + problemId,
                    attemptCount,
                    rate
            ));
        }
        report.setTrendingProblems(trendingProblems);

        // Hardest problems (lowest completion rate)
        List<Problem> publishedProblems = problemMapper.selectList(
                new LambdaQueryWrapper<Problem>().eq(Problem::getStatus, "PUBLISHED")
        );

        List<ProblemCompletionReportVO.HardestProblem> hardestProblems = publishedProblems.stream()
                .limit(10)
                .map(problem -> {
                    long attemptsForProblem = submissionMapper.selectCount(
                            new LambdaQueryWrapper<Submission>()
                                    .eq(Submission::getProblemId, problem.getId())
                    );
                    long acceptedCount = submissionMapper.selectCount(
                            new LambdaQueryWrapper<Submission>()
                                    .eq(Submission::getProblemId, problem.getId())
                                    .eq(Submission::getStatus, "Accepted")
                    );
                    double rate = attemptsForProblem > 0 ? (acceptedCount * 100.0 / attemptsForProblem) : 0.0;
                    return new ProblemCompletionReportVO.HardestProblem(
                            problem.getId().toString(),
                            problem.getTitle(),
                            problem.getDifficulty(),
                            rate
                    );
                })
                .sorted((a, b) -> Double.compare(a.getCompletionRate(), b.getCompletionRate()))
                .collect(Collectors.toList());
        report.setHardestProblems(hardestProblems);

        return report;
    }

    @Override
    public ContestParticipationReportVO getContestParticipationReport(Integer days) {
        int daysToAnalyze = days != null && days > 0 ? days : 30;
        LocalDateTime startDate = LocalDateTime.now().minusDays(daysToAnalyze);

        ContestParticipationReportVO report = new ContestParticipationReportVO();

        // Total contests in period
        LambdaQueryWrapper<Contest> contestWrapper = new LambdaQueryWrapper<>();
        contestWrapper.ge(Contest::getStartTime, startDate);
        long totalContestsCount = contestMapper.selectCount(contestWrapper);
        report.setTotalContests((int) totalContestsCount);

        // Get contests in period
        List<Contest> contests = contestMapper.selectList(contestWrapper);

        // Total unique participants
        Set<String> uniqueParticipants = new HashSet<>();
        for (Contest contest : contests) {
            List<ContestParticipant> participants = contestParticipantMapper.selectList(
                    new LambdaQueryWrapper<ContestParticipant>()
                            .eq(ContestParticipant::getContestId, contest.getId())
            );
            participants.forEach(p -> uniqueParticipants.add(p.getUserId()));
        }
        report.setTotalParticipants((long) uniqueParticipants.size());

        // Average participants per contest
        double avgParticipants = totalContestsCount > 0 ? (double) uniqueParticipants.size() / totalContestsCount : 0.0;
        report.setAverageParticipantsPerContest(avgParticipants);

        // By type
        Map<String, ContestParticipationReportVO.TypeStats> typeStatsMap = new HashMap<>();
        for (Contest contest : contests) {
            long participantCount = contestParticipantMapper.selectCount(
                    new LambdaQueryWrapper<ContestParticipant>()
                            .eq(ContestParticipant::getContestId, contest.getId())
            );
            typeStatsMap.merge(contest.getContestType(),
                    new ContestParticipationReportVO.TypeStats(contest.getContestType(), 1, (double) participantCount),
                    (existing, newValue) -> new ContestParticipationReportVO.TypeStats(
                            contest.getContestType(),
                            existing.getCount() + 1,
                            (existing.getAvgParticipants() * existing.getCount() + participantCount) / (existing.getCount() + 1)
                    )
            );
        }
        report.setByType(new ArrayList<>(typeStatsMap.values()));

        // Top contests
        List<ContestParticipationReportVO.TopContest> topContests = contests.stream()
                .limit(10)
                .map(contest -> {
                    long participants = contestParticipantMapper.selectCount(
                            new LambdaQueryWrapper<ContestParticipant>()
                                    .eq(ContestParticipant::getContestId, contest.getId())
                    );
                    return new ContestParticipationReportVO.TopContest(
                            contest.getId(),
                            contest.getTitle(),
                            (int) participants,
                            100.0 // Default completion rate
                    );
                })
                .sorted((a, b) -> Integer.compare(b.getParticipants(), a.getParticipants()))
                .collect(Collectors.toList());
        report.setTopContests(topContests);

        // Virtual participation (placeholder)
        ContestParticipationReportVO.VirtualParticipation virtualParticipation =
                new ContestParticipationReportVO.VirtualParticipation(0, 0.0);
        report.setVirtualParticipation(virtualParticipation);

        // Participation trend
        List<ContestParticipationReportVO.ParticipationTrend> trend = new ArrayList<>();
        for (int i = (daysToAnalyze / 7); i >= 0; i--) {
            LocalDateTime weekStart = LocalDateTime.now().minusWeeks(i).withHour(0).withMinute(0).withSecond(0);
            LocalDateTime weekEnd = weekStart.plusWeeks(1);

            LambdaQueryWrapper<Contest> weekContestWrapper = new LambdaQueryWrapper<>();
            weekContestWrapper.ge(Contest::getStartTime, weekStart)
                    .lt(Contest::getStartTime, weekEnd);

            List<Contest> weekContests = contestMapper.selectList(weekContestWrapper);
            Set<String> weekParticipants = new HashSet<>();
            for (Contest contest : weekContests) {
                List<ContestParticipant> participants = contestParticipantMapper.selectList(
                        new LambdaQueryWrapper<ContestParticipant>()
                                .eq(ContestParticipant::getContestId, contest.getId())
                );
                participants.forEach(p -> weekParticipants.add(p.getUserId()));
            }

            trend.add(new ContestParticipationReportVO.ParticipationTrend(
                    weekStart.toLocalDate().toString(),
                    weekContests.size(),
                    weekParticipants.size()
            ));
        }
        report.setParticipationTrend(trend);

        return report;
    }

    @Override
    public RevenueReportVO getRevenueReport(Integer days) {
        int daysToAnalyze = days != null && days > 0 ? days : 30;
        LocalDateTime startDate = LocalDateTime.now().minusDays(daysToAnalyze);

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
            LocalDateTime dayStart = LocalDateTime.now().minusDays(i).withHour(0).withMinute(0).withSecond(0);
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
        resourceUsage.setCpu(-1.0); // CPU requires OS-level access; -1 indicates unavailable in-app
        resourceUsage.setMemory(Math.round(memoryUsagePercent * 100.0) / 100.0);
        resourceUsage.setDisk(-1.0); // Disk requires OS-level access; -1 indicates unavailable in-app
        report.setResourceUsage(resourceUsage);

        // Performance metrics - these require external monitoring (APM tool); -1 indicates unavailable
        report.setAverageResponseTime(-1.0);
        report.setErrorRate(-1.0);
        report.setThroughput(-1L);
        report.setCacheHitRate(-1.0);

        // Slowest endpoints - requires request timing middleware
        report.setSlowestEndpoints(Collections.emptyList());

        // Error breakdown - requires error tracking integration
        report.setErrorBreakdown(Collections.emptyList());

        return report;
    }

    // ==================== Private Helper Methods ====================

    /**
     * Calculate retention rate for a given day.
     * Uses COUNT(DISTINCT user_id) aggregation queries for accurate distinct user counting.
     * Previous implementation used selectCount with groupBy which returns the count of the
     * first group only, not the total distinct user count (MyBatis-Plus Pitfall 4).
     *
     * NOTE: This is an approximation using distinct user counts rather than
     * a true set intersection. For exact retention, a dedicated
     * subquery-based approach or materialized view is needed.
     */
    private Double calculateRetentionRate(int dayN) {
        LocalDateTime day0 = LocalDateTime.now().minusDays(dayN);
        LocalDateTime dayNDate = day0.plusDays(dayN);

        LocalDateTime day0Start = day0.withHour(0).withMinute(0).withSecond(0);
        LocalDateTime day0End = day0Start.plusDays(1);
        LocalDateTime dayNStart = dayNDate.withHour(0).withMinute(0).withSecond(0);
        LocalDateTime dayNEnd = dayNStart.plusDays(1);

        long day0DistinctUsers = submissionMapper.countDistinctUsersInRange(day0Start, day0End);

        if (day0DistinctUsers == 0) {
            return 0.0;
        }

        long dayNDistinctUsers = submissionMapper.countDistinctUsersInRange(dayNStart, dayNEnd);

        // Approximate retention: ratio of distinct active users on day N vs day 0
        return Math.min(dayNDistinctUsers * 100.0 / day0DistinctUsers, 100.0);
    }

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
