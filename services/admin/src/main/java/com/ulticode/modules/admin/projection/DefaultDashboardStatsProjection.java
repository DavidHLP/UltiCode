package com.ulticode.modules.admin.projection;

import com.ulticode.auth.api.dto.AccountQueryDTO;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.dto.ChartDataPoint;
import com.ulticode.modules.admin.dto.ChartStatsVO;
import com.ulticode.modules.admin.dto.DashboardStatsVO;
import com.ulticode.modules.admin.mapper.DashboardMapper;
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
 * sub-aggregators) and 3 {@code default} methods on {@code DashboardMapper}.
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

    private final DashboardMapper dashboardMapper;
    private final Clock clock;

    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private AccountQueryService accountQueryService;

    @Value("${app.version:1.0.0}")
    private String appVersion;

    @Autowired
    public DefaultDashboardStatsProjection(DashboardMapper dashboardMapper, Clock clock) {
        this.dashboardMapper = dashboardMapper;
        this.clock = clock;
    }

    public DefaultDashboardStatsProjection(DashboardMapper dashboardMapper, Clock clock,
                                           AccountQueryService accountQueryService) {
        this(dashboardMapper, clock);
        this.accountQueryService = accountQueryService;
    }

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
        List<Map<String, Object>> rawData = fetchChartData(metric, startDate, now, dateFormat, period);
        result.setData(toChartDataPoints(rawData));

        return result;
    }

    // ---------- sub-aggregators ----------

    private DashboardStatsVO.UserStats buildUserStats() {
        DashboardStatsVO.UserStats stats = new DashboardStatsVO.UserStats();
        stats.setTotal(0L);
        stats.setActive(0L);
        stats.setBanned(0L);
        stats.setActiveToday(0L);
        stats.setActiveWeek(0L);
        stats.setActiveMonth(0L);
        stats.setByRole(new HashMap<>());

        AccountScan scan = scanAccounts(null);

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime todayStart = now.minusDays(1);
        LocalDateTime weekStart = now.minusWeeks(1);
        LocalDateTime monthStart = now.minusMonths(1);
        long active = 0L;
        long banned = 0L;
        long activeToday = 0L;
        long activeWeek = 0L;
        long activeMonth = 0L;
        Map<String, Long> roleCounts = new HashMap<>();

        for (AuthAccountDTO account : scan.accounts()) {
            if (account.active()) {
                active++;
            }
            if (account.banned()) {
                banned++;
            }
            if (account.lastLoginAt() != null) {
                if (!account.lastLoginAt().isBefore(todayStart)) {
                    activeToday++;
                }
                if (!account.lastLoginAt().isBefore(weekStart)) {
                    activeWeek++;
                }
                if (!account.lastLoginAt().isBefore(monthStart)) {
                    activeMonth++;
                }
            }
            if (account.role() != null) {
                roleCounts.merge(account.role(), 1L, Long::sum);
            }
        }

        stats.setTotal(scan.total());
        stats.setActive(active);
        stats.setBanned(banned);
        stats.setActiveToday(activeToday);
        stats.setActiveWeek(activeWeek);
        stats.setActiveMonth(activeMonth);
        stats.setByRole(roleCounts);
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
                                                      LocalDateTime end, String dateFormat,
                                                      String period) {
        return switch (metric.toLowerCase()) {
            case "users" -> fetchUserChartData(start, end, period);
            case "submissions" -> dashboardMapper.getSubmissionsChartData(start, end, dateFormat);
            case "problems" -> dashboardMapper.getProblemsChartData(start, end, dateFormat);
            case "contests" -> dashboardMapper.getContestsChartData(start, end, dateFormat);
            case "solutions" -> dashboardMapper.getSolutionsChartData(start, end, dateFormat);
            case "forum_posts" -> dashboardMapper.getForumPostsChartData(start, end, dateFormat);
            default -> List.of();
        };
    }
    private List<Map<String, Object>> fetchUserChartData(LocalDateTime start, LocalDateTime end,
                                                          String period) {
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
                .map(entry -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("date", entry.getKey());
                    row.put("count", entry.getValue());
                    return row;
                })
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

    private record AccountScan(List<AuthAccountDTO> accounts, long total) {
    }

    // ---------- Map -> Map<String,Long> shape rules (previously default methods on mapper) ----------


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
