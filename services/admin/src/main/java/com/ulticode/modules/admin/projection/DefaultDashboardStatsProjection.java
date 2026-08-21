package com.ulticode.modules.admin.projection;

import com.ulticode.app.api.dto.DashboardAppStatsDTO;
import com.ulticode.modules.admin.dto.ChartDataPoint;
import com.ulticode.modules.admin.dto.ChartStatsVO;
import com.ulticode.modules.admin.dto.DashboardStatsVO;
import com.ulticode.modules.admin.port.AdminDashboardReadPort;
import com.ulticode.submission.api.dto.SubmissionDashboardStatsDTO;
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
 * sub-aggregators) and direct foreign-table mapper queries.
 */
@Component
public class DefaultDashboardStatsProjection implements DashboardStatsProjection {

    private final AdminDashboardReadPort dashboardReadPort;
    private final Clock clock;

    @Value("${app.version:1.0.0}")
    private String appVersion;

    public DefaultDashboardStatsProjection(AdminDashboardReadPort dashboardReadPort, Clock clock) {
        this.dashboardReadPort = dashboardReadPort;
        this.clock = clock;
    }

    @Override
    public DashboardStatsVO loadStats() {
        LocalDateTime now = LocalDateTime.now(clock);
        AdminDashboardReadPort.DashboardData dashboardData = dashboardReadPort.loadStats(now);
        DashboardStatsVO stats = new DashboardStatsVO();
        stats.setUsers(buildUserStats(dashboardData.users()));
        stats.setProblems(buildProblemStats(dashboardData.app()));
        stats.setContests(buildContestStats(dashboardData.app()));
        stats.setSubmissions(buildSubmissionStats(dashboardData.submission()));
        stats.setSolutions(buildSolutionStats(dashboardData.app()));
        stats.setForum(buildForumStats(dashboardData.app()));
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

        List<AdminDashboardReadPort.ChartPoint> rawData =
                fetchChartData(metric, startDate, now, period);
        result.setData(toChartDataPoints(rawData));

        return result;
    }

    // ---------- sub-aggregators ----------

    private DashboardStatsVO.UserStats buildUserStats(AdminDashboardReadPort.DashboardUserData data) {
        DashboardStatsVO.UserStats stats = new DashboardStatsVO.UserStats();
        stats.setTotal(data.total());
        stats.setActive(data.active());
        stats.setBanned(data.banned());
        stats.setActiveToday(data.activeToday());
        stats.setActiveWeek(data.activeWeek());
        stats.setActiveMonth(data.activeMonth());
        stats.setByRole(data.byRole());
        return stats;
    }

    private DashboardStatsVO.ProblemStats buildProblemStats(DashboardAppStatsDTO data) {
        DashboardStatsVO.ProblemStats stats = new DashboardStatsVO.ProblemStats();

        stats.setTotal(data.totalProblems());
        stats.setPublished(data.publishedProblems());
        stats.setUnpublished(stats.getTotal() - stats.getPublished());
        stats.setByDifficulty(shapeCounts(data.problemsByDifficulty()));
        stats.setByStatus(shapeCounts(data.problemsByStatus()));

        return stats;
    }

    private DashboardStatsVO.ContestStats buildContestStats(DashboardAppStatsDTO data) {
        DashboardStatsVO.ContestStats stats = new DashboardStatsVO.ContestStats();

        stats.setTotal(data.totalContests());
        stats.setUpcoming(data.upcomingContests());
        stats.setRunning(data.runningContests());
        stats.setFinished(data.finishedContests());

        return stats;
    }

    private DashboardStatsVO.SubmissionStats buildSubmissionStats(SubmissionDashboardStatsDTO data) {
        DashboardStatsVO.SubmissionStats stats = new DashboardStatsVO.SubmissionStats();

        stats.setTotal(data.total());
        stats.setToday(data.today());
        stats.setWeek(data.week());
        stats.setMonth(data.month());
        stats.setAcceptanceRate(data.acceptanceRate());

        return stats;
    }

    private DashboardStatsVO.SolutionStats buildSolutionStats(DashboardAppStatsDTO data) {
        DashboardStatsVO.SolutionStats stats = new DashboardStatsVO.SolutionStats();

        stats.setTotal(data.totalSolutions());
        stats.setPublished(data.publishedSolutions());
        stats.setFlagged(data.flaggedSolutions());

        return stats;
    }

    private DashboardStatsVO.ForumStats buildForumStats(DashboardAppStatsDTO data) {
        DashboardStatsVO.ForumStats stats = new DashboardStatsVO.ForumStats();

        stats.setPosts(data.forumPosts());
        stats.setComments(data.forumComments());
        stats.setCommunities(data.forumCommunities());
        stats.setFlaggedPosts(data.flaggedForumPosts());
        stats.setFlaggedComments(data.flaggedForumComments());

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

    private List<AdminDashboardReadPort.ChartPoint> fetchChartData(
            String metric, LocalDateTime start, LocalDateTime end, String period) {
        return switch (metric.toLowerCase()) {
            case "users", "submissions", "problems", "contests", "solutions", "forum_posts" ->
                    dashboardReadPort.loadChartData(metric, start, end, period);
            default -> List.of();
        };
    }

    private List<ChartDataPoint> toChartDataPoints(List<AdminDashboardReadPort.ChartPoint> rawData) {
        return rawData.stream()
                .map(row -> {
                    ChartDataPoint point = new ChartDataPoint();
                    point.setDate(row.date());
                    point.setCount(row.count());
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

    // ---------- Owner count -> Admin Map shape rules ----------
    private Map<String, Long> shapeCounts(List<DashboardAppStatsDTO.Count> raw) {
        Map<String, Long> result = new HashMap<>();
        for (DashboardAppStatsDTO.Count row : raw) {
            if (row.key() != null) {
                result.put(row.key(), row.count());
            }
        }
        return result;
    }
}
