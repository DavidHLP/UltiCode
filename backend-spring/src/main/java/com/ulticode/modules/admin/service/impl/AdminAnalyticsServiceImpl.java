package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.admin.dto.*;
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

    @Override
    public UserActivityReportVO getUserActivityReport(Integer days) {
        int daysToAnalyze = days != null && days > 0 ? days : 30;
        LocalDateTime startDate = LocalDateTime.now().minusDays(daysToAnalyze);

        UserActivityReportVO report = new UserActivityReportVO();

        // Daily active users (based on submissions)
        List<UserActivityReportVO.DailyActiveUsers> dailyActiveUsers = new ArrayList<>();
        for (int i = daysToAnalyze - 1; i >= 0; i--) {
            LocalDateTime dayStart = LocalDateTime.now().minusDays(i).withHour(0).withMinute(0).withSecond(0);
            LocalDateTime dayEnd = dayStart.plusDays(1);

            LambdaQueryWrapper<Submission> wrapper = new LambdaQueryWrapper<>();
            wrapper.ge(Submission::getCreatedAt, dayStart)
                    .lt(Submission::getCreatedAt, dayEnd)
                    .isNotNull(Submission::getUserId)
                    .select(Submission::getUserId);

            List<Submission> submissions = submissionMapper.selectList(wrapper);
            long activeUsers = submissions.stream()
                    .map(Submission::getUserId)
                    .distinct()
                    .count();

            dailyActiveUsers.add(new UserActivityReportVO.DailyActiveUsers(
                    dayStart.toLocalDate().toString(),
                    (int) activeUsers
            ));
        }
        report.setActiveUsersDaily(dailyActiveUsers);

        // Weekly active users
        List<UserActivityReportVO.DailyActiveUsers> weeklyActiveUsers = new ArrayList<>();
        for (int i = (daysToAnalyze / 7); i >= 0; i--) {
            LocalDateTime weekStart = LocalDateTime.now().minusWeeks(i).withHour(0).withMinute(0).withSecond(0);
            LocalDateTime weekEnd = weekStart.plusWeeks(1);

            LambdaQueryWrapper<Submission> wrapper = new LambdaQueryWrapper<>();
            wrapper.ge(Submission::getCreatedAt, weekStart)
                    .lt(Submission::getCreatedAt, weekEnd);

            List<Submission> submissions = submissionMapper.selectList(wrapper);
            long activeUsers = submissions.stream()
                    .map(Submission::getUserId)
                    .distinct()
                    .count();

            weeklyActiveUsers.add(new UserActivityReportVO.DailyActiveUsers(
                    weekStart.toLocalDate().toString(),
                    (int) activeUsers
            ));
        }
        report.setActiveUsersWeekly(weeklyActiveUsers);

        // User retention (simplified calculation)
        UserActivityReportVO.UserRetention retention = new UserActivityReportVO.UserRetention();
        retention.setDay1(calculateRetentionRate(1));
        retention.setDay7(calculateRetentionRate(7));
        retention.setDay30(calculateRetentionRate(30));
        report.setUserRetention(retention);

        // Peak active hours
        List<UserActivityReportVO.PeakActiveHour> peakHours = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            LocalDateTime hourStart = LocalDateTime.now().withHour(hour).withMinute(0).withSecond(0);
            LocalDateTime hourEnd = hourStart.plusHours(1);

            LambdaQueryWrapper<Submission> wrapper = new LambdaQueryWrapper<>();
            wrapper.ge(Submission::getCreatedAt, hourStart)
                    .lt(Submission::getCreatedAt, hourEnd);

            long count = submissionMapper.selectCount(wrapper);
            peakHours.add(new UserActivityReportVO.PeakActiveHour(hour, (int) count));
        }
        // Sort by count descending and take top 24
        peakHours.sort((a, b) -> Long.compare(b.getCount(), a.getCount()));
        report.setPeakActiveHours(peakHours.stream().limit(24).collect(Collectors.toList()));

        // Top active users (by submission count)
        Map<String, Long> userSubmissionCounts = new HashMap<>();
        LambdaQueryWrapper<Submission> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(Submission::getCreatedAt, startDate);

        List<Submission> recentSubmissions = submissionMapper.selectList(wrapper);
        for (Submission submission : recentSubmissions) {
            userSubmissionCounts.merge(submission.getUserId(), 1L, Long::sum);
        }

        List<UserActivityReportVO.TopActiveUser> topUsers = userSubmissionCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(entry -> {
                    User user = userMapper.selectById(entry.getKey());
                    return new UserActivityReportVO.TopActiveUser(
                            entry.getKey(),
                            user != null ? user.getUsername() : "Unknown",
                            entry.getValue().intValue(),
                            user != null ? user.getLastLoginAt() : null
                    );
                })
                .collect(Collectors.toList());
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

        // By difficulty
        List<ProblemCompletionReportVO.DifficultyStats> byDifficulty = Arrays.asList("EASY", "MEDIUM", "HARD").stream()
                .map(difficulty -> {
                    LambdaQueryWrapper<Problem> problemWrapper = new LambdaQueryWrapper<>();
                    problemWrapper.eq(Problem::getDifficulty, difficulty)
                            .eq(Problem::getStatus, "PUBLISHED");

                    List<Problem> problems = problemMapper.selectList(problemWrapper);
                    int totalProblems = problems.size();

                    int solvedProblems = 0;
                    for (Problem problem : problems) {
                        LambdaQueryWrapper<Submission> subWrapper = new LambdaQueryWrapper<>();
                        subWrapper.eq(Submission::getProblemId, problem.getId())
                                .eq(Submission::getStatus, "Accepted");
                        if (submissionMapper.selectCount(subWrapper) > 0) {
                            solvedProblems++;
                        }
                    }

                    double rate = totalProblems > 0 ? (solvedProblems * 100.0 / totalProblems) : 0.0;
                    return new ProblemCompletionReportVO.DifficultyStats(difficulty, totalProblems, solvedProblems, rate);
                })
                .collect(Collectors.toList());
        report.setByDifficulty(byDifficulty);

        // By tag (top 10)
        List<ProblemTag> allTags = problemTagMapper.selectList(new LambdaQueryWrapper<>());
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

        // Trending problems (most attempted)
        Map<Long, Long> problemAttempts = new HashMap<>();
        for (Submission submission : submissionMapper.selectList(allWrapper)) {
            problemAttempts.merge(submission.getProblemId(), 1L, Long::sum);
        }

        List<ProblemCompletionReportVO.TrendingProblem> trendingProblems = problemAttempts.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(10)
                .map(entry -> {
                    Problem problem = problemMapper.selectById(entry.getKey());
                    long acceptedCount = submissionMapper.selectCount(
                            new LambdaQueryWrapper<Submission>()
                                    .eq(Submission::getProblemId, entry.getKey())
                                    .eq(Submission::getStatus, "Accepted")
                    );
                    double rate = entry.getValue() > 0 ? (acceptedCount * 100.0 / entry.getValue()) : 0.0;
                    return new ProblemCompletionReportVO.TrendingProblem(
                            entry.getKey().toString(),
                            problem != null ? problem.getTitle() : "Problem " + entry.getKey(),
                            entry.getValue().intValue(),
                            rate
                    );
                })
                .collect(Collectors.toList());
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

        // Resource usage (placeholder values - in production would use actual monitoring)
        PerformanceReportVO.ResourceUsage resourceUsage = new PerformanceReportVO.ResourceUsage();
        resourceUsage.setCpu(25.0);   // Placeholder
        resourceUsage.setMemory(45.0); // Placeholder
        resourceUsage.setDisk(60.0);   // Placeholder
        report.setResourceUsage(resourceUsage);

        // Performance metrics (placeholders)
        report.setAverageResponseTime(150.0); // 150ms average
        report.setErrorRate(0.5); // 0.5% error rate
        report.setThroughput(10000L); // 10k requests per 24h
        report.setCacheHitRate(85.0); // 85% cache hit rate

        // Slowest endpoints (placeholder data)
        List<PerformanceReportVO.SlowEndpoint> slowEndpoints = Arrays.asList(
                new PerformanceReportVO.SlowEndpoint("/api/problems", 350.0, 1500),
                new PerformanceReportVO.SlowEndpoint("/api/submissions", 280.0, 2300),
                new PerformanceReportVO.SlowEndpoint("/api/contests", 220.0, 800)
        );
        report.setSlowestEndpoints(slowEndpoints);

        // Error breakdown (placeholder data)
        List<PerformanceReportVO.ErrorBreakdown> errorBreakdown = Arrays.asList(
                new PerformanceReportVO.ErrorBreakdown("400 Bad Request", 15, 30.0),
                new PerformanceReportVO.ErrorBreakdown("401 Unauthorized", 25, 50.0),
                new PerformanceReportVO.ErrorBreakdown("404 Not Found", 10, 20.0)
        );
        report.setErrorBreakdown(errorBreakdown);

        return report;
    }

    // ==================== Private Helper Methods ====================

    /**
     * Calculate retention rate for a given day.
     * Simplified calculation based on users who submitted on day 0 and day N.
     */
    private Double calculateRetentionRate(int dayN) {
        LocalDateTime day0 = LocalDateTime.now().minusDays(dayN);
        LocalDateTime dayNDate = day0.plusDays(dayN);

        // Users who submitted on day 0
        LambdaQueryWrapper<Submission> day0Wrapper = new LambdaQueryWrapper<>();
        day0Wrapper.ge(Submission::getCreatedAt, day0.withHour(0).withMinute(0).withSecond(0))
                .lt(Submission::getCreatedAt, day0.withHour(23).withMinute(59).withSecond(59));

        List<Submission> day0Submissions = submissionMapper.selectList(day0Wrapper);
        Set<String> day0Users = day0Submissions.stream()
                .map(Submission::getUserId)
                .collect(Collectors.toSet());

        if (day0Users.isEmpty()) {
            return 0.0;
        }

        // Users who submitted on day N
        LambdaQueryWrapper<Submission> dayNWrapper = new LambdaQueryWrapper<>();
        dayNWrapper.ge(Submission::getCreatedAt, dayNDate.withHour(0).withMinute(0).withSecond(0))
                .lt(Submission::getCreatedAt, dayNDate.withHour(23).withMinute(59).withSecond(59));

        List<Submission> dayNSubmissions = submissionMapper.selectList(dayNWrapper);
        Set<String> dayNUsers = dayNSubmissions.stream()
                .map(Submission::getUserId)
                .collect(Collectors.toSet());

        // Calculate retention
        day0Users.retainAll(dayNUsers);
        return day0Users.size() * 100.0 / day0Users.size();
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
