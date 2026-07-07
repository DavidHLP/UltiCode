package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ulticode.modules.admin.dto.ChartDataPoint;
import com.ulticode.modules.admin.dto.ChartStatsVO;
import com.ulticode.modules.admin.dto.DashboardStatsVO;
import com.ulticode.modules.admin.mapper.DashboardMapper;
import com.ulticode.modules.admin.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard service implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final DashboardMapper dashboardMapper;
    private final Clock clock;

    @Value("${app.version:1.0.0}")
    private String appVersion;

    @Override
    public DashboardStatsVO getStats() {
        DashboardStatsVO stats = new DashboardStatsVO();

        // User stats
        stats.setUsers(getUserStats());

        // Problem stats
        stats.setProblems(getProblemStats());

        // Contest stats
        stats.setContests(getContestStats());

        // Submission stats
        stats.setSubmissions(getSubmissionStats());

        // Solution stats
        stats.setSolutions(getSolutionStats());

        // Forum stats
        stats.setForum(getForumStats());

        // System stats
        stats.setSystem(getSystemStats());

        return stats;
    }

    private DashboardStatsVO.UserStats getUserStats() {
        DashboardStatsVO.UserStats stats = new DashboardStatsVO.UserStats();
        LocalDateTime now = LocalDateTime.now(clock);

        stats.setTotal(dashboardMapper.countTotalUsers());
        stats.setActive(dashboardMapper.countActiveUsers());
        stats.setBanned(dashboardMapper.countBannedUsers());
        stats.setActiveToday(dashboardMapper.countActiveUsersSince(now.minusDays(1)));
        stats.setActiveWeek(dashboardMapper.countActiveUsersSince(now.minusWeeks(1)));
        stats.setActiveMonth(dashboardMapper.countActiveUsersSince(now.minusMonths(1)));
        stats.setByRole(dashboardMapper.countUsersByRole());

        return stats;
    }

    private DashboardStatsVO.ProblemStats getProblemStats() {
        DashboardStatsVO.ProblemStats stats = new DashboardStatsVO.ProblemStats();

        stats.setTotal(dashboardMapper.countTotalProblems());
        stats.setPublished(dashboardMapper.countPublishedProblems());
        stats.setUnpublished(stats.getTotal() - stats.getPublished());
        stats.setByDifficulty(dashboardMapper.countProblemsByDifficulty());
        stats.setByStatus(dashboardMapper.countProblemsByStatus());

        return stats;
    }

    private DashboardStatsVO.ContestStats getContestStats() {
        DashboardStatsVO.ContestStats stats = new DashboardStatsVO.ContestStats();
        LocalDateTime now = LocalDateTime.now(clock);

        stats.setTotal(dashboardMapper.countTotalContests());
        stats.setUpcoming(dashboardMapper.countUpcomingContests(now));
        stats.setRunning(dashboardMapper.countRunningContests(now));
        stats.setFinished(dashboardMapper.countFinishedContests(now));

        return stats;
    }

    private DashboardStatsVO.SubmissionStats getSubmissionStats() {
        DashboardStatsVO.SubmissionStats stats = new DashboardStatsVO.SubmissionStats();
        LocalDateTime now = LocalDateTime.now(clock);

        stats.setTotal(dashboardMapper.countTotalSubmissions());
        stats.setToday(dashboardMapper.countSubmissionsSince(now.minusDays(1)));
        stats.setWeek(dashboardMapper.countSubmissionsSince(now.minusWeeks(1)));
        stats.setMonth(dashboardMapper.countSubmissionsSince(now.minusMonths(1)));
        stats.setAcceptanceRate(dashboardMapper.calculateAcceptanceRate());

        return stats;
    }

    private DashboardStatsVO.SolutionStats getSolutionStats() {
        DashboardStatsVO.SolutionStats stats = new DashboardStatsVO.SolutionStats();

        stats.setTotal(dashboardMapper.countTotalSolutions());
        stats.setPublished(dashboardMapper.countPublishedSolutions());
        stats.setFlagged(dashboardMapper.countFlaggedSolutions());

        return stats;
    }

    private DashboardStatsVO.ForumStats getForumStats() {
        DashboardStatsVO.ForumStats stats = new DashboardStatsVO.ForumStats();

        stats.setPosts(dashboardMapper.countForumPosts());
        stats.setComments(dashboardMapper.countForumComments());
        stats.setCommunities(dashboardMapper.countForumCommunities());
        stats.setFlaggedPosts(dashboardMapper.countFlaggedPosts());
        stats.setFlaggedComments(dashboardMapper.countFlaggedComments());

        return stats;
    }

    private DashboardStatsVO.SystemStats getSystemStats() {
        DashboardStatsVO.SystemStats stats = new DashboardStatsVO.SystemStats();

        // Uptime in milliseconds
        long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
        stats.setUptime(uptimeMillis / 1000); // Convert to seconds
        stats.setVersion(appVersion);

        return stats;
    }

    @Override
    public ChartStatsVO getChartStats(String metric, String period, Integer days) {
        ChartStatsVO result = new ChartStatsVO();
        result.setMetric(metric);
        result.setPeriod(period);

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime startDate;

        if (days != null && days > 0) {
            startDate = now.minusDays(days);
        } else {
            startDate = getDefaultStartDate(period);
        }

        result.setStartDate(startDate);
        result.setEndDate(now);

        List<ChartDataPoint> data = getChartData(metric, period, startDate, now);
        result.setData(data);

        return result;
    }

    private LocalDateTime getDefaultStartDate(String period) {
        LocalDateTime now = LocalDateTime.now(clock);
        return switch (period.toLowerCase()) {
            case "hour" -> now.minusHours(24);
            case "day" -> now.minusDays(30);
            case "week" -> now.minusWeeks(12);
            case "month" -> now.minusMonths(12);
            case "year" -> now.minusYears(5);
            default -> now.minusDays(30);
        };
    }

    private List<ChartDataPoint> getChartData(String metric, String period, LocalDateTime start, LocalDateTime end) {
        String dateFormat = getDateFormat(period);
        List<Map<String, Object>> rawData = switch (metric.toLowerCase()) {
            case "users" -> dashboardMapper.getUsersChartData(start, end, dateFormat);
            case "submissions" -> dashboardMapper.getSubmissionsChartData(start, end, dateFormat);
            case "problems" -> dashboardMapper.getProblemsChartData(start, end, dateFormat);
            case "contests" -> dashboardMapper.getContestsChartData(start, end, dateFormat);
            case "solutions" -> dashboardMapper.getSolutionsChartData(start, end, dateFormat);
            case "forum_posts" -> dashboardMapper.getForumPostsChartData(start, end, dateFormat);
            default -> List.of();
        };

        // Convert Map<String, Object> to ChartDataPoint
        return rawData.stream()
                .map(row -> {
                    ChartDataPoint point = new ChartDataPoint();
                    point.setDate((String) row.get("date"));
                    Object countObj = row.get("count");
                    if (countObj instanceof Number) {
                        point.setCount(((Number) countObj).longValue());
                    } else {
                        point.setCount(0L);
                    }
                    return point;
                })
                .toList();
    }

    private String getDateFormat(String period) {
        return switch (period.toLowerCase()) {
            case "hour" -> "%Y-%m-%d %H:00";
            case "day" -> "%Y-%m-%d";
            case "week" -> "%Y-%u";
            case "month" -> "%Y-%m";
            case "year" -> "%Y";
            default -> "%Y-%m-%d";
        };
    }
}
