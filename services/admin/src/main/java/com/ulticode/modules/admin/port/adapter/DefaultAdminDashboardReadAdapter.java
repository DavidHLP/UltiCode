package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.dto.DashboardAppStatsDTO;
import com.ulticode.app.api.dto.DashboardChartDataDTO;
import com.ulticode.app.api.service.DashboardAdminReadPort;
import com.ulticode.auth.api.dto.AccountQueryDTO;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.port.AdminDashboardReadPort;
import com.ulticode.submission.api.dto.SubmissionDashboardChartDataDTO;
import com.ulticode.submission.api.dto.SubmissionDashboardStatsDTO;
import com.ulticode.submission.api.service.SubmissionAdminReadPort;
import jakarta.annotation.PreDestroy;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Bounded Admin Dashboard adapter. Owner-specific SQL remains behind the App
 * and Submission contracts; Dashboard projection callers cross this one seam.
 */
@Component
public class DefaultAdminDashboardReadAdapter implements AdminDashboardReadPort {
    private static final int ACCOUNT_PAGE_SIZE = 100;
    private static final DateTimeFormatter HOUR_BUCKET_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH");
    private static final DateTimeFormatter DAY_BUCKET_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_BUCKET_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM");

    private final SubmissionAdminReadPort submissionAdminReadPort;
    private final CancellableQueryExecutor queryExecutor;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private DashboardAdminReadPort appDashboardReadPort;

    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private AccountQueryService accountQueryService;

    @Autowired
    public DefaultAdminDashboardReadAdapter(SubmissionAdminReadPort submissionAdminReadPort) {
        this(submissionAdminReadPort, new CancellableQueryExecutor("admin-dashboard-query", 4));
    }

    DefaultAdminDashboardReadAdapter(
            SubmissionAdminReadPort submissionAdminReadPort,
            CancellableQueryExecutor queryExecutor) {
        this.submissionAdminReadPort = submissionAdminReadPort;
        this.queryExecutor = queryExecutor;
    }

    @PreDestroy
    void shutdownQueryExecutor() {
        queryExecutor.close();
    }

    @Override
    public DashboardData loadStats(LocalDateTime now) {
        CancellableQueryExecutor.Query<DashboardAppStatsDTO> appFuture = queryExecutor.submit(
                () -> appDashboardReadPort.loadDashboardStats(now));
        CancellableQueryExecutor.Query<SubmissionDashboardStatsDTO> submissionFuture = queryExecutor.submit(
                () -> submissionAdminReadPort.loadDashboardStats(now));
        CancellableQueryExecutor.Query<DashboardUserData> userFuture = queryExecutor.submit(this::loadUserData);
        try {
            CompletableFuture.allOf(appFuture.result(), submissionFuture.result(), userFuture.result())
                    .get(RpcPolicy.QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            DashboardAppStatsDTO app = appFuture.result().get();
            SubmissionDashboardStatsDTO submission = submissionFuture.result().get();
            DashboardUserData users = userFuture.result().get();
            if (app == null || submission == null || users == null) {
                throw unavailable();
            }
            return new DashboardData(users, app, submission);
        } catch (BusinessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            CancellableQueryExecutor.cancel(appFuture, submissionFuture, userFuture);
            throw unavailable();
        } catch (ExecutionException exception) {
            CancellableQueryExecutor.cancel(appFuture, submissionFuture, userFuture);
            Throwable cause = exception.getCause();
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            if (cause instanceof BusinessException) {
                throw (BusinessException) cause;
            }
            throw unavailable();
        } catch (TimeoutException exception) {
            CancellableQueryExecutor.cancel(appFuture, submissionFuture, userFuture);
            throw unavailable();
        }
    }

    @Override
    public List<ChartPoint> loadChartData(
            String metric, LocalDateTime start, LocalDateTime end, String period) {
        try {
            return switch (metric) {
                case "users" -> userPoints(start, end, period);
                case "submissions" -> submissionPoints(
                        submissionAdminReadPort.loadDashboardChartData(start, end, period));
                case "problems", "contests", "solutions", "forum_posts" -> appPoints(
                        appDashboardReadPort.loadDashboardChartData(metric, start, end, period));
                default -> List.of();
            };
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    private static List<ChartPoint> appPoints(List<DashboardChartDataDTO> rows) {
        if (rows == null) {
            throw unavailable();
        }
        return rows.stream().map(row -> new ChartPoint(row.date(), row.count())).toList();
    }

    private static List<ChartPoint> submissionPoints(List<SubmissionDashboardChartDataDTO> rows) {
        if (rows == null) {
            throw unavailable();
        }
        return rows.stream().map(row -> new ChartPoint(row.date(), row.count())).toList();
    }

    private DashboardUserData loadUserData() {
        if (accountQueryService == null) {
            throw unavailable();
        }
        RpcResult<AccountQueryService.AccountStatsSummary> response =
                accountQueryService.getDashboardStatsSummary();
        if (response == null || !response.success() || response.data() == null) {
            throw unavailable();
        }
        AccountQueryService.AccountStatsSummary summary = response.data();
        return new DashboardUserData(
                summary.total(), summary.active(), summary.banned(), summary.activeToday(),
                summary.activeWeek(), summary.activeMonth(), summary.byRole());
    }

    private List<ChartPoint> userPoints(LocalDateTime start, LocalDateTime end, String period) {
        AccountScan scan = scanAccounts(start);
        if (scan.accounts().isEmpty()) {
            return List.of();
        }
        Map<String, Long> buckets = new TreeMap<>();
        for (var account : scan.accounts()) {
            LocalDateTime joinedAt = account.joinedAt();
            if (!joinedAt.isBefore(start) && !joinedAt.isAfter(end)) {
                buckets.merge(formatTimeBucket(joinedAt, period), 1L, Long::sum);
            }
        }
        return buckets.entrySet().stream()
                .map(entry -> new ChartPoint(entry.getKey(), entry.getValue()))
                .toList();
    }

    private AccountScan scanAccounts(LocalDateTime stopBefore) {
        if (accountQueryService == null) {
            throw unavailable();
        }
        try {
            List<com.ulticode.auth.api.dto.AuthAccountDTO> accounts = new ArrayList<>();
            long total = -1L;
            for (int pageNumber = 1; ; pageNumber++) {
                RpcResult<com.ulticode.auth.api.dto.AuthAccountDTO> response =
                        accountQueryService.queryAccounts(new AccountQueryDTO(
                                null, null, null, null, pageNumber,
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
                long expectedPages = page.total() == 0L
                        ? 0L : (page.total() - 1L) / page.pageSize() + 1L;
                if (page.totalPages() != expectedPages) {
                    throw unavailable();
                }
                if (page.total() != 0L && pageNumber > page.totalPages()) {
                    throw unavailable();
                }
                if (total < 0L) {
                    total = page.total();
                } else if (total != page.total()) {
                    throw unavailable();
                }
                List<?> items = page.items();
                if (items == null || items.size() > page.pageSize() || items.isEmpty()) {
                    if (total == 0L && items != null && items.isEmpty()) {
                        return new AccountScan(accounts, total);
                    }
                    throw unavailable();
                }
                if (pageNumber < page.totalPages() && items.size() < page.pageSize()) {
                    throw unavailable();
                }
                boolean reachedWindow = false;
                for (Object item : items) {
                    if (!(item instanceof com.ulticode.auth.api.dto.AuthAccountDTO account)
                            || account.joinedAt() == null
                            || account.role() == null || account.role().isBlank()) {
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
            throw unavailable();
        }
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

    private record AccountScan(
            List<com.ulticode.auth.api.dto.AuthAccountDTO> accounts, long total) {
    }

    private static BusinessException unavailable() {
        return new BusinessException(BaseErrorCode.UNKNOWN_ERROR, "Dashboard owner unavailable");
    }

}
