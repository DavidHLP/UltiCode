package com.ulticode.modules.admin.port.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ulticode.app.api.dto.DashboardAppStatsDTO;
import com.ulticode.app.api.service.DashboardAdminReadPort;
import com.ulticode.auth.api.dto.AuthUserTrendBucketDTO;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.time.FakeTimeSource;
import com.ulticode.modules.admin.metrics.AdminUseCaseMetrics;
import com.ulticode.modules.admin.port.AdminDashboardReadPort;
import com.ulticode.submission.api.dto.SubmissionDashboardStatsDTO;
import com.ulticode.submission.api.service.SubmissionAdminReadPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DefaultAdminDashboardReadAdapterMetricsTest {

    @Mock
    private DashboardAdminReadPort appDashboardReadPort;
    @Mock
    private SubmissionAdminReadPort submissionAdminReadPort;
    @Mock
    private AccountQueryService accountQueryService;

    private DefaultAdminDashboardReadAdapter adapter;

    @AfterEach
    void closeAdapter() {
        if (adapter != null) {
            adapter.shutdownQueryExecutor();
        }
    }

    @Test
    void dashboardStatsExposeOwnerFanoutAndFreshness() {
        when(appDashboardReadPort.loadDashboardStats(any())).thenReturn(
                new DashboardAppStatsDTO(1, 1, List.of(), List.of(), 2, 1, 0, 1,
                        3, 2, 1, 4, 5, 6, 0, 0));
        when(submissionAdminReadPort.loadDashboardStats(any()))
                .thenReturn(new SubmissionDashboardStatsDTO(7, 1, 2, 3, 65.5));
        when(accountQueryService.getDashboardStatsSummary()).thenReturn(
                RpcResult.success(new AccountQueryService.AccountStatsSummary(
                        9, 8, 1, 2, 3, 4, java.util.Map.of("USER", 8L)), "test"));

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        adapter = adapter(new AdminUseCaseMetrics(registry, new FakeTimeSource()));

        AdminDashboardReadPort.DashboardData result =
                adapter.loadStats(LocalDateTime.of(2026, 8, 20, 10, 0));

        assertThat(result.users().total()).isEqualTo(9);
        for (String owner : List.of("APP", "AUTH", "SUBMISSION")) {
            assertThat(registry.find(AdminUseCaseMetrics.LOGICAL_CALLS)
                    .tags("use_case", "I-DASH-STATS", "owner", owner)
                    .summary().max()).isEqualTo(1D);
        }
        assertThat(registry.find(AdminUseCaseMetrics.SERIAL_ROUNDS)
                .tags("use_case", "I-DASH-STATS", "owner", "all")
                .summary().max()).isEqualTo(1D);
        assertThat(registry.find(AdminUseCaseMetrics.FRESHNESS)
                .tags("use_case", "I-DASH-STATS", "owner", "all", "freshness", "NOW")
                .counter().count()).isEqualTo(1D);
    }

    @Test
    void userTrendUsesOneAuthCallMetric() {
        when(accountQueryService.getUserTrend(any())).thenReturn(
                RpcResult.success(List.of(new AuthUserTrendBucketDTO("2026-08-01", 2)), "test"));

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        adapter = adapter(new AdminUseCaseMetrics(registry, new FakeTimeSource()));

        assertThat(adapter.loadChartData(
                "users",
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 1, 23, 59),
                "day")).hasSize(1);
        assertThat(registry.find(AdminUseCaseMetrics.LOGICAL_CALLS)
                .tags("use_case", "I-DASH-CHART-USERS", "owner", "AUTH")
                .summary().max()).isEqualTo(1D);
    }

    private DefaultAdminDashboardReadAdapter adapter(AdminUseCaseMetrics metrics) {
        adapter = new DefaultAdminDashboardReadAdapter(submissionAdminReadPort);
        ReflectionTestUtils.setField(adapter, "appDashboardReadPort", appDashboardReadPort);
        ReflectionTestUtils.setField(adapter, "accountQueryService", accountQueryService);
        ReflectionTestUtils.setField(adapter, "useCaseMetrics", metrics);
        return adapter;
    }
}
