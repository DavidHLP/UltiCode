package com.ulticode.modules.admin.projection;

import com.ulticode.modules.admin.dto.ChartDataPoint;
import com.ulticode.modules.admin.dto.ChartStatsVO;
import com.ulticode.modules.admin.dto.DashboardStatsVO;
import com.ulticode.modules.admin.mapper.DashboardMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of {@link DashboardStatsProjection}.
 *
 * <p>Owns the entity&rarr;VO shaping rule for all 7 dashboard stat blocks plus the chart-data
 * dispatcher. Previously this logic was spread across {@code DashboardServiceImpl} (7 private
 * sub-aggregators) and 3 {@code default} methods on {@code DashboardMapper}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultDashboardStatsProjection implements DashboardStatsProjection {

    private final DashboardMapper dashboardMapper;
    private final Clock clock;

    @Value("${app.version:1.0.0}")
    private String appVersion;

    @Override
    public DashboardStatsVO loadStats() {
        DashboardStatsVO stats = new DashboardStatsVO();
        stats.setUsers(buildUserStats());
        stats.setProblems(buildProblemStats());
        stats.setContests(buildContestStats());
        stats.setSubmissions(buildSubmissionStats());
        stats.setSolutions(buildSolutionStats());
        stats.setForum(buildForumStats());
        stats.setSystem(buildSystemStats());
        return stats;
    }

    @Override
    public ChartStatsVO loadChartStats(String metric, String period, Integer days) {
        ChartStatsVO result = new ChartStatsVO();
        result.setMetric(metric);
        result.setPeriod(period);

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime startDate;

        if (days != null && days > 0) {
            startDate = now.minusDays(days);
        } else {
            startDate = getDefaultStartDate(period, now);
        }

        result.setStartDate(startDate);
        result.setEndDate(now);

        String dateFormat = getDateFormat(period);
        List<Map<String, Object>> rawData = fetchChartData(metric, startDate, now, dateFormat);
        result.setData(toChartDataPoints(rawData));

        return result;
    }

    // ---------- sub-aggregators ----------

    private DashboardStatsVO.UserStats buildUserStats() {
        DashboardStatsVO.UserStats stats = new DashboardStatsVO.UserStats();
        LocalDateTime now = LocalDateTime.now(clock);

        stats.setTotal(dashboardMapper.countTotalUsers());
        stats.setActive(dashboardMapper.countActiveUsers());
        stats.setBanned(dashboardMapper.countBannedUsers());
        stats.setActiveToday(dashboardMapper.countActiveUsersSince(now.minusDays(1)));
        stats.setActiveWeek(dashboardMapper.countActiveUsersSince(now.minusWeeks(1)));
        stats.setActiveMonth(dashboardMapper.countActiveUsersSince(now.minusMonths(1)));
        stats.setByRole(shapeRoleCounts(dashboardMapper.countUsersByRoleRaw()));

        return stats;
    }

    private DashboardStatsVO.ProblemStats buildProblemStats() {
        DashboardStatsVO.ProblemStats stats = new DashboardStatsVO.ProblemStats();

        stats.setTotal(dashboardMapper.countTotalProblems());
        stats.setPublished(dashboardMapper.countPublishedProblems());
        stats.setUnpublished(stats.getTotal() - stats.getPublished());
        stats.setByDifficulty(shapeCounts(dashboardMapper.countProblemsByDifficultyRaw(), "difficulty"));
        stats.setByStatus(shapeCounts(dashboardMapper.countProblemsByStatusRaw(), "status"));

        return stats;
    }

    private DashboardStatsVO.ContestStats buildContestStats() {
        DashboardStatsVO.ContestStats stats = new DashboardStatsVO.ContestStats();
        LocalDateTime now = LocalDateTime.now(clock);

        stats.setTotal(dashboardMapper.countTotalContests());
        stats.setUpcoming(dashboardMapper.countUpcomingContests(now));
        stats.setRunning(dashboardMapper.countRunningContests(now));
        stats.setFinished(dashboardMapper.countFinishedContests(now));

        return stats;
    }

    private DashboardStatsVO.SubmissionStats buildSubmissionStats() {
        DashboardStatsVO.SubmissionStats stats = new DashboardStatsVO.SubmissionStats();
        LocalDateTime now = LocalDateTime.now(clock);

        stats.setTotal(dashboardMapper.countTotalSubmissions());
        stats.setToday(dashboardMapper.countSubmissionsSince(now.minusDays(1)));
        stats.setWeek(dashboardMapper.countSubmissionsSince(now.minusWeeks(1)));
        stats.setMonth(dashboardMapper.countSubmissionsSince(now.minusMonths(1)));
        stats.setAcceptanceRate(dashboardMapper.calculateAcceptanceRate());

        return stats;
    }

    private DashboardStatsVO.SolutionStats buildSolutionStats() {
        DashboardStatsVO.SolutionStats stats = new DashboardStatsVO.SolutionStats();

        stats.setTotal(dashboardMapper.countTotalSolutions());
        stats.setPublished(dashboardMapper.countPublishedSolutions());
        stats.setFlagged(dashboardMapper.countFlaggedSolutions());

        return stats;
    }

    private DashboardStatsVO.ForumStats buildForumStats() {
        DashboardStatsVO.ForumStats stats = new DashboardStatsVO.ForumStats();

        stats.setPosts(dashboardMapper.countForumPosts());
        stats.setComments(dashboardMapper.countForumComments());
        stats.setCommunities(dashboardMapper.countForumCommunities());
        stats.setFlaggedPosts(dashboardMapper.countFlaggedPosts());
        stats.setFlaggedComments(dashboardMapper.countFlaggedComments());

        return stats;
    }

    private DashboardStatsVO.SystemStats buildSystemStats() {
        DashboardStatsVO.SystemStats stats = new DashboardStatsVO.SystemStats();
        long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
        stats.setUptime(uptimeMillis / 1000);
        stats.setVersion(appVersion);
        return stats;
    }

    // ---------- chart data ----------

    private List<Map<String, Object>> fetchChartData(String metric, LocalDateTime start,
                                                      LocalDateTime end, String dateFormat) {
        return switch (metric.toLowerCase()) {
            case "users" -> dashboardMapper.getUsersChartData(start, end, dateFormat);
            case "submissions" -> dashboardMapper.getSubmissionsChartData(start, end, dateFormat);
            case "problems" -> dashboardMapper.getProblemsChartData(start, end, dateFormat);
            case "contests" -> dashboardMapper.getContestsChartData(start, end, dateFormat);
            case "solutions" -> dashboardMapper.getSolutionsChartData(start, end, dateFormat);
            case "forum_posts" -> dashboardMapper.getForumPostsChartData(start, end, dateFormat);
            default -> List.of();
        };
    }

    private List<ChartDataPoint> toChartDataPoints(List<Map<String, Object>> rawData) {
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

    private LocalDateTime getDefaultStartDate(String period, LocalDateTime now) {
        return switch (period.toLowerCase()) {
            case "hour" -> now.minusHours(24);
            case "day" -> now.minusDays(30);
            case "week" -> now.minusWeeks(12);
            case "month" -> now.minusMonths(12);
            case "year" -> now.minusYears(5);
            default -> now.minusDays(30);
        };
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

    // ---------- Map -> Map<String,Long> shape rules (previously default methods on mapper) ----------

    private Map<String, Long> shapeRoleCounts(List<Map<String, Object>> raw) {
        Map<String, Long> result = new HashMap<>();
        for (Map<String, Object> row : raw) {
            result.put((String) row.get("role"), ((Number) row.get("count")).longValue());
        }
        return result;
    }

    private Map<String, Long> shapeCounts(List<Map<String, Object>> raw, String keyColumn) {
        Map<String, Long> result = new HashMap<>();
        for (Map<String, Object> row : raw) {
            if (row.get(keyColumn) != null) {
                result.put((String) row.get(keyColumn), ((Number) row.get("count")).longValue());
            }
        }
        return result;
    }
}
