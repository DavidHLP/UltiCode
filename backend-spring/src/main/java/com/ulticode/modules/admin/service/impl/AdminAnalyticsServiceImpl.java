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
import com.ulticode.modules.subscription.entity.Subscription;
import com.ulticode.modules.subscription.mapper.SubscriptionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
        return performanceReportService.getPerformanceReport();
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
