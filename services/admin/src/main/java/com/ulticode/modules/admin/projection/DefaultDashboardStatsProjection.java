package com.ulticode.modules.admin.projection;

import com.ulticode.auth.api.dto.AccountQueryDTO;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.app.api.dto.DashboardAppStatsDTO;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.dto.ChartDataPoint;
import com.ulticode.modules.admin.dto.ChartStatsVO;
import com.ulticode.modules.admin.dto.DashboardStatsVO;
import com.ulticode.modules.admin.port.AdminDashboardReadPort;
import com.ulticode.submission.api.dto.SubmissionDashboardStatsDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Default implementation of {@link DashboardStatsProjection}.
 *
 * <p>Owns the entity&rarr;VO shaping rule for all 7 dashboard stat blocks plus the chart-data
 * dispatcher. Previously this logic was spread across {@code DashboardServiceImpl} (7 private
 * sub-aggregators) and direct foreign-table mapper queries.
 */
@Slf4j
@Component
public class DefaultDashboardStatsProjection implements DashboardStatsProjection {

    private static final int ACCOUNT_PAGE_SIZE = 100;
    private static final DateTimeFormatter HOUR_BUCKET_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH");
    private static final DateTimeFormatter DAY_BUCKET_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_BUCKET_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM");

    private final AdminDashboardReadPort dashboardReadPort;
    private final Clock clock;

    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private AccountQueryService accountQueryService;

    @Value("${app.version:1.0.0}")
    private String appVersion;

    @Autowired
    public DefaultDashboardStatsProjection(AdminDashboardReadPort dashboardReadPort, Clock clock) {
        this.dashboardReadPort = dashboardReadPort;
        this.clock = clock;
    }

    public DefaultDashboardStatsProjection(AdminDashboardReadPort dashboardReadPort, Clock clock,
                                           AccountQueryService accountQueryService) {
        this(dashboardReadPort, clock);
        this.accountQueryService = accountQueryService;
    }

    @Override
    public DashboardStatsVO loadStats() {
        LocalDateTime now = LocalDateTime.now(clock);
        AdminDashboardReadPort.DashboardData dashboardData = dashboardReadPort.loadStats(now);
        DashboardStatsVO stats = new DashboardStatsVO();
        stats.setUsers(buildUserStats());
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

    private DashboardStatsVO.UserStats buildUserStats() {
        DashboardStatsVO.UserStats stats = new DashboardStatsVO.UserStats();
        if (accountQueryService == null) {
            throw unavailable();
        }
        RpcResult<AccountQueryService.AccountStatsSummary> response =
                accountQueryService.getDashboardStatsSummary();
        if (response == null || !response.success() || response.data() == null) {
            throw unavailable();
        }
        AccountQueryService.AccountStatsSummary summary = response.data();
        stats.setTotal(summary.total());
        stats.setActive(summary.active());
        stats.setBanned(summary.banned());
        stats.setActiveToday(summary.activeToday());
        stats.setActiveWeek(summary.activeWeek());
        stats.setActiveMonth(summary.activeMonth());
        stats.setByRole(summary.byRole());
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
            case "users" -> fetchUserChartData(start, end, period);
            case "submissions", "problems", "contests", "solutions", "forum_posts" ->
                    dashboardReadPort.loadChartData(metric, start, end, period);
            default -> List.of();
        };
    }
    private List<AdminDashboardReadPort.ChartPoint> fetchUserChartData(
            LocalDateTime start, LocalDateTime end, String period) {
        AccountScan scan = scanAccounts(start);
        if (scan.accounts().isEmpty()) {
            return List.of();
        }

        Map<String, Long> buckets = new TreeMap<>();
        for (AuthAccountDTO account : scan.accounts()) {
            LocalDateTime joinedAt = account.joinedAt();
            if (joinedAt.isBefore(start) || joinedAt.isAfter(end)) {
                continue;
            }
            buckets.merge(formatTimeBucket(joinedAt, period), 1L, Long::sum);
        }

        return buckets.entrySet().stream()
                .map(entry -> new AdminDashboardReadPort.ChartPoint(entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * Read Auth accounts in joined-at descending order. A non-null lower bound lets the
     * caller stop as soon as the ordered page contains an older account.
     */
    private AccountScan scanAccounts(LocalDateTime stopBefore) {
        if (accountQueryService == null) {
            throw unavailable();
        }

        try {
            List<AuthAccountDTO> accounts = new ArrayList<>();
            long total = -1L;
            for (int pageNumber = 1; ; pageNumber++) {
                RpcResult<AuthAccountDTO> response = accountQueryService.queryAccounts(
                        new AccountQueryDTO(null, null, null, null, pageNumber,
                                ACCOUNT_PAGE_SIZE, "joinedAt", "desc"));
                if (response == null || !response.success() || response.page() == null) {
                    throw unavailable();
                }

                RpcResult.Page page = response.page();
                if (page.page() == null || page.page() != pageNumber
                        || page.pageSize() == null || page.pageSize() < 1
                        || page.pageSize() > ACCOUNT_PAGE_SIZE
                        || page.total() == null || page.total() < 0
                        || page.totalPages() == null || page.totalPages() < 0) {
                    throw unavailable();
                }
                long expectedTotalPages = page.total() == 0L
                        ? 0L
                        : (page.total() - 1L) / page.pageSize() + 1L;
                if (page.totalPages() != expectedTotalPages) {
                    throw unavailable();
                }
                if (total < 0L) {
                    total = page.total();
                } else if (total != page.total()) {
                    throw unavailable();
                }

                List<?> pageItems = page.items();
                if (pageItems == null || pageItems.size() > page.pageSize()) {
                    throw unavailable();
                }
                if (pageItems.isEmpty()) {
                    if (total == 0L) {
                        return new AccountScan(accounts, total);
                    }
                    throw unavailable();
                }
                if (pageNumber < page.totalPages() && pageItems.size() < page.pageSize()) {
                    throw unavailable();
                }
                if (page.totalPages() == 0 || pageNumber > page.totalPages()) {
                    throw unavailable();
                }

                boolean reachedWindow = false;
                for (Object item : pageItems) {
                    if (!(item instanceof AuthAccountDTO account)
                            || account.joinedAt() == null
                            || account.role() == null
                            || account.role().isBlank()) {
                        throw unavailable();
                    }
                    if (!reachedWindow && stopBefore != null && account.joinedAt().isBefore(stopBefore)) {
                        reachedWindow = true;
                    }
                    if (!reachedWindow) {
                        accounts.add(account);
                    }
                }

                if (reachedWindow || pageNumber >= page.totalPages()) {
                    if (!reachedWindow && accounts.size() != total) {
                        throw unavailable();
                    }
                    return new AccountScan(accounts, total);
                }
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("Auth account query unavailable while loading dashboard");
            throw unavailable();
        }
    }

    private BusinessException unavailable() {
        return new BusinessException(BaseErrorCode.UNKNOWN_ERROR, "Auth account owner unavailable");
    }

    private String formatTimeBucket(LocalDateTime value, String period) {
        return switch (period.toLowerCase()) {
            case "hour" -> value.format(HOUR_BUCKET_FORMAT) + ":00";
            case "week" -> String.format(java.util.Locale.ROOT, "%04d-%02d",
                    value.getYear(), value.get(WeekFields.ISO.weekOfYear()));
            case "month" -> value.format(MONTH_BUCKET_FORMAT);
            case "year" -> String.format(java.util.Locale.ROOT, "%04d", value.getYear());
            default -> value.format(DAY_BUCKET_FORMAT);
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

    private record AccountScan(List<AuthAccountDTO> accounts, long total) {
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
