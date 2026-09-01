package com.ulticode.modules.admin.port.adapter;

import com.ulticode.admin.error.AdminReadContract;
import com.ulticode.app.api.dto.DashboardAppStatsDTO;
import com.ulticode.app.api.dto.DashboardChartDataDTO;
import com.ulticode.app.api.service.DashboardAdminReadPort;
import com.ulticode.auth.api.dto.AuthUserTrendAggregateQuery;
import com.ulticode.auth.api.dto.AuthUserTrendBucketDTO;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.metrics.AdminUseCaseMetrics;
import com.ulticode.modules.admin.port.AdminDashboardReadPort;
import com.ulticode.submission.api.dto.SubmissionDashboardChartDataDTO;
import com.ulticode.submission.api.dto.SubmissionDashboardStatsDTO;
import com.ulticode.submission.api.service.SubmissionAdminReadPort;
import jakarta.annotation.PreDestroy;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Bounded Admin Dashboard adapter. Owner-specific SQL remains behind the App
 * and Submission contracts; Dashboard projection callers cross this one seam.
 */
@Component
public class DefaultAdminDashboardReadAdapter implements AdminDashboardReadPort {
    private static final int MAX_TREND_BUCKETS = AuthUserTrendAggregateQuery.MAX_BUCKETS;
    private static final Map<AdminUseCaseMetrics.Owner, Integer> STATS_CALLS = Map.of(
            AdminUseCaseMetrics.Owner.APP, 1,
            AdminUseCaseMetrics.Owner.AUTH, 1,
            AdminUseCaseMetrics.Owner.SUBMISSION, 1);
    private static final Map<AdminUseCaseMetrics.Owner, Integer> APP_CHART_CALLS =
            Map.of(AdminUseCaseMetrics.Owner.APP, 1);
    private static final Map<AdminUseCaseMetrics.Owner, Integer> SUBMISSION_CHART_CALLS =
            Map.of(AdminUseCaseMetrics.Owner.SUBMISSION, 1);
    private static final Map<AdminUseCaseMetrics.Owner, Integer> USER_CHART_CALLS =
            Map.of(AdminUseCaseMetrics.Owner.AUTH, 1);

    private final SubmissionAdminReadPort submissionAdminReadPort;
    private final CancellableQueryExecutor queryExecutor;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private DashboardAdminReadPort appDashboardReadPort;

    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private AccountQueryService accountQueryService;


    /** Optional so focused wiring tests and metrics-disabled deployments retain the same seam. */
    @Autowired(required = false)
    private AdminUseCaseMetrics useCaseMetrics;

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
        return observe(
                "I-DASH-STATS",
                STATS_CALLS,
                1,
                AdminUseCaseMetrics.Freshness.NOW,
                () -> loadStatsInternal(now));
    }

    private DashboardData loadStatsInternal(LocalDateTime now) {
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
        if (metric == null) {
            throw unavailable();
        }
        String useCase = switch (metric) {
            case "users" -> "I-DASH-CHART-USERS";
            case "submissions", "problems", "contests", "solutions", "forum_posts"
                    -> "I-DASH-CHART-OWNER";
            default -> null;
        };
        if (useCase == null) {
            return List.of();
        }
        Map<AdminUseCaseMetrics.Owner, Integer> calls = switch (metric) {
            case "users" -> USER_CHART_CALLS;
            case "submissions" -> SUBMISSION_CHART_CALLS;
            default -> APP_CHART_CALLS;
        };
        return observe(
                useCase,
                calls,
                1,
                AdminUseCaseMetrics.Freshness.REQ,
                () -> loadChartDataInternal(metric, start, end, period));
    }

    private List<ChartPoint> loadChartDataInternal(
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
        if (accountQueryService == null || start == null || end == null
                || start.isAfter(end) || period == null || period.isBlank()) {
            throw unavailable();
        }
        AuthUserTrendAggregateQuery query =
                new AuthUserTrendAggregateQuery(start, end, period, MAX_TREND_BUCKETS);
        RpcResult<List<AuthUserTrendBucketDTO>> response =
                accountQueryService.getUserTrend(query);
        if (response == null || !response.success() || response.data() == null) {
            throw unavailable();
        }
        List<AuthUserTrendBucketDTO> buckets = response.data();
        if (buckets.size() > MAX_TREND_BUCKETS) {
            throw unavailable();
        }
        List<ChartPoint> points = new ArrayList<>(buckets.size());
        String previousDate = null;
        for (AuthUserTrendBucketDTO bucket : buckets) {
            if (bucket == null || bucket.date() == null || bucket.date().isBlank()
                    || bucket.count() < 0
                    || (previousDate != null && previousDate.compareTo(bucket.date()) >= 0)) {
                throw unavailable();
            }
            points.add(new ChartPoint(bucket.date(), bucket.count()));
            previousDate = bucket.date();
        }
        return List.copyOf(points);
    }

    private <T> T observe(
            String useCase,
            Map<AdminUseCaseMetrics.Owner, Integer> logicalCallsByOwner,
            int serialRounds,
            AdminUseCaseMetrics.Freshness freshness,
            Supplier<T> action) {
        AdminUseCaseMetrics metrics = useCaseMetrics;
        return metrics == null
                ? action.get()
                : metrics.observe(useCase, logicalCallsByOwner, serialRounds, freshness, action);
    }

    private static BusinessException unavailable() {
        return AdminReadContract.ownerUnavailable("Dashboard");
    }

}
