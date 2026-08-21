package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.dto.DashboardAppStatsDTO;
import com.ulticode.app.api.dto.DashboardChartDataDTO;
import com.ulticode.app.api.service.DashboardAdminReadPort;
import com.ulticode.common.dto.DashboardBucketCount;
import com.ulticode.modules.dashboard.mapper.DashboardAdminMapper;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/** App-owner provider for the entity-free Admin Dashboard read seam. */
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class DashboardAdminReadProvider implements DashboardAdminReadPort {

    private final DashboardAdminMapper dashboardAdminMapper;

    @Override
    public DashboardAppStatsDTO loadDashboardStats(LocalDateTime now) {
        return new DashboardAppStatsDTO(
                count(dashboardAdminMapper.countTotalProblems()),
                count(dashboardAdminMapper.countPublishedProblems()),
                counts(dashboardAdminMapper.countProblemsByDifficulty()),
                counts(dashboardAdminMapper.countProblemsByStatus()),
                count(dashboardAdminMapper.countTotalContests()),
                count(dashboardAdminMapper.countUpcomingContests(now)),
                count(dashboardAdminMapper.countRunningContests(now)),
                count(dashboardAdminMapper.countFinishedContests(now)),
                count(dashboardAdminMapper.countTotalSolutions()),
                count(dashboardAdminMapper.countPublishedSolutions()),
                count(dashboardAdminMapper.countFlaggedSolutions()),
                count(dashboardAdminMapper.countForumPosts()),
                count(dashboardAdminMapper.countForumComments()),
                count(dashboardAdminMapper.countForumCommunities()),
                count(dashboardAdminMapper.countFlaggedForumPosts()),
                count(dashboardAdminMapper.countFlaggedForumComments()));
    }

    @Override
    public List<DashboardChartDataDTO> loadDashboardChartData(
            String metric, LocalDateTime start, LocalDateTime end, String period) {
        String dateFormat = dateFormat(period);
        List<DashboardBucketCount> rows = switch (metric == null ? "" : metric.toLowerCase(Locale.ROOT)) {
            case "problems" -> dashboardAdminMapper.chartProblems(start, end, dateFormat);
            case "contests" -> dashboardAdminMapper.chartContests(start, end, dateFormat);
            case "solutions" -> dashboardAdminMapper.chartSolutions(start, end, dateFormat);
            case "forum_posts" -> dashboardAdminMapper.chartForumPosts(start, end, dateFormat);
            default -> List.of();
        };
        return rows == null ? List.of() : rows.stream()
                .map(row -> new DashboardChartDataDTO(row.getBucket(), count(row.getCount())))
                .toList();
    }

    private static List<DashboardAppStatsDTO.Count> counts(List<DashboardBucketCount> rows) {
        if (rows == null) {
            return List.of();
        }
        return rows.stream()
                .filter(row -> row.getBucket() != null)
                .map(row -> new DashboardAppStatsDTO.Count(row.getBucket(), count(row.getCount())))
                .toList();
    }

    private static long count(Number value) {
        return value == null ? 0L : value.longValue();
    }

    private static long count(Long value) {
        return value == null ? 0L : value;
    }

    private static String dateFormat(String period) {
        return switch (period == null ? "" : period.toLowerCase(Locale.ROOT)) {
            case "hour" -> "%Y-%m-%d %H:00";
            case "week" -> "%Y-%u";
            case "month" -> "%Y-%m";
            case "year" -> "%Y";
            default -> "%Y-%m-%d";
        };
    }
}
