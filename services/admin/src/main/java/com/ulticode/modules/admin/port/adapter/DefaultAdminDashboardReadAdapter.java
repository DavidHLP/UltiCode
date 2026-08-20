package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.dto.DashboardAppStatsDTO;
import com.ulticode.app.api.dto.DashboardChartDataDTO;
import com.ulticode.app.api.service.DashboardAdminReadPort;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.modules.admin.port.AdminDashboardReadPort;
import com.ulticode.submission.api.dto.SubmissionDashboardChartDataDTO;
import com.ulticode.submission.api.dto.SubmissionDashboardStatsDTO;
import com.ulticode.submission.api.service.SubmissionAdminReadPort;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Bounded Admin Dashboard adapter. Owner-specific SQL remains behind the App
 * and Submission contracts; Dashboard projection callers cross this one seam.
 */
@Component
@RequiredArgsConstructor
public class DefaultAdminDashboardReadAdapter implements AdminDashboardReadPort {

    private final SubmissionAdminReadPort submissionAdminReadPort;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private DashboardAdminReadPort appDashboardReadPort;

    @Override
    public DashboardData loadStats(LocalDateTime now) {
        CompletableFuture<DashboardAppStatsDTO> appFuture = CompletableFuture.supplyAsync(
                () -> appDashboardReadPort.loadDashboardStats(now));
        CompletableFuture<SubmissionDashboardStatsDTO> submissionFuture = CompletableFuture.supplyAsync(
                () -> submissionAdminReadPort.loadDashboardStats(now));
        try {
            CompletableFuture.allOf(appFuture, submissionFuture)
                    .get(RpcPolicy.QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            DashboardAppStatsDTO app = appFuture.get();
            SubmissionDashboardStatsDTO submission = submissionFuture.get();
            if (app == null || submission == null) {
                throw unavailable();
            }
            return new DashboardData(app, submission);
        } catch (BusinessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            cancel(appFuture, submissionFuture);
            throw unavailable();
        } catch (ExecutionException | TimeoutException exception) {
            cancel(appFuture, submissionFuture);
            throw unavailable();
        }
    }

    @Override
    public List<ChartPoint> loadChartData(
            String metric, LocalDateTime start, LocalDateTime end, String period) {
        try {
            return switch (metric) {
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

    private static BusinessException unavailable() {
        return new BusinessException(BaseErrorCode.UNKNOWN_ERROR, "Dashboard owner unavailable");
    }

    private static void cancel(
            CompletableFuture<?> appFuture, CompletableFuture<?> submissionFuture) {
        appFuture.cancel(true);
        submissionFuture.cancel(true);
    }
}
