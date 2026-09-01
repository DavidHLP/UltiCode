package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.dto.DashboardAppStatsDTO;
import com.ulticode.app.api.dto.DashboardChartDataDTO;
import com.ulticode.app.api.service.DashboardAdminReadPort;
import com.ulticode.auth.api.dto.AuthUserTrendAggregateQuery;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.auth.api.dto.AuthUserTrendBucketDTO;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.modules.admin.port.AdminDashboardReadPort;
import com.ulticode.submission.api.dto.SubmissionDashboardChartDataDTO;
import com.ulticode.submission.api.dto.SubmissionDashboardStatsDTO;
import com.ulticode.submission.api.service.SubmissionAdminReadPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultAdminDashboardReadAdapterTest {

    @Mock
    private DashboardAdminReadPort appDashboardReadPort;
    @Mock
    private SubmissionAdminReadPort submissionAdminReadPort;
    @Mock
    private AccountQueryService accountQueryService;

    @Test
    void loadStatsUsesOneBoundedCallPerOwner() {
        DashboardAppStatsDTO app = new DashboardAppStatsDTO(
                1, 1, List.of(), List.of(), 2, 1, 0, 1,
                3, 2, 1, 4, 5, 6, 0, 0);
        SubmissionDashboardStatsDTO submission = new SubmissionDashboardStatsDTO(7, 1, 2, 3, 65.5);
        DefaultAdminDashboardReadAdapter adapter = adapter();
        when(appDashboardReadPort.loadDashboardStats(any())).thenReturn(app);
        when(submissionAdminReadPort.loadDashboardStats(any())).thenReturn(submission);
        when(accountQueryService.getDashboardStatsSummary()).thenReturn(
                RpcResult.success(new AccountQueryService.AccountStatsSummary(
                        9, 8, 1, 2, 3, 4, java.util.Map.of("USER", 8L)), "test"));

        AdminDashboardReadPort.DashboardData result =
                adapter.loadStats(LocalDateTime.of(2026, 8, 20, 10, 0));

        assertThat(result.app()).isSameAs(app);
        assertThat(result.submission()).isSameAs(submission);
        assertThat(result.users().total()).isEqualTo(9);
        verify(appDashboardReadPort, times(1)).loadDashboardStats(any());
        verify(submissionAdminReadPort, times(1)).loadDashboardStats(any());
    }

    @Test
    void routesSubmissionChartToSubmissionOwner() {
        DefaultAdminDashboardReadAdapter adapter = adapter();
        when(submissionAdminReadPort.loadDashboardChartData(any(), any(), any()))
                .thenReturn(List.of(new SubmissionDashboardChartDataDTO("2026-08-20", 2L)));

        List<AdminDashboardReadPort.ChartPoint> result = adapter.loadChartData(
                "submissions",
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 20, 0, 0),
                "day");

        assertThat(result).containsExactly(new AdminDashboardReadPort.ChartPoint("2026-08-20", 2L));
        verify(submissionAdminReadPort).loadDashboardChartData(any(), any(), any());
    }

    @Test
    void routesAppChartToAppOwner() {
        DefaultAdminDashboardReadAdapter adapter = adapter();
        when(appDashboardReadPort.loadDashboardChartData(
                org.mockito.ArgumentMatchers.eq("problems"), any(), any(), any()))
                .thenReturn(List.of(new DashboardChartDataDTO("2026-08-20", 3L)));

        List<AdminDashboardReadPort.ChartPoint> result = adapter.loadChartData(
                "problems",
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 20, 0, 0),
                "day");

        assertThat(result).containsExactly(new AdminDashboardReadPort.ChartPoint("2026-08-20", 3L));
        verify(appDashboardReadPort).loadDashboardChartData(
                org.mockito.ArgumentMatchers.eq("problems"), any(), any(), any());
    }

    @Test
    void routesUserChartToOneBoundedAuthAggregateCall() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 3, 23, 59);
        when(accountQueryService.getUserTrend(any())).thenReturn(
                RpcResult.success(List.of(
                        new AuthUserTrendBucketDTO("2026-08-01", 2L),
                        new AuthUserTrendBucketDTO("2026-08-03", 1L)), "test"));

        List<AdminDashboardReadPort.ChartPoint> result =
                adapter().loadChartData("users", start, end, "day");

        assertThat(result).containsExactly(
                new AdminDashboardReadPort.ChartPoint("2026-08-01", 2L),
                new AdminDashboardReadPort.ChartPoint("2026-08-03", 1L));
        ArgumentCaptor<AuthUserTrendAggregateQuery> query =
                ArgumentCaptor.forClass(AuthUserTrendAggregateQuery.class);
        verify(accountQueryService, times(1)).getUserTrend(query.capture());
        assertThat(query.getValue().start()).isEqualTo(start);
        assertThat(query.getValue().end()).isEqualTo(end);
        assertThat(query.getValue().period()).isEqualTo("day");
        assertThat(query.getValue().maxBuckets())
                .isEqualTo(AuthUserTrendAggregateQuery.MAX_BUCKETS);
    }

    @Test
    void failsClosedWhenAnOwnerIsUnavailable() {
        DefaultAdminDashboardReadAdapter adapter = adapter();
        when(appDashboardReadPort.loadDashboardStats(any()))
                .thenThrow(new IllegalStateException("owner offline"));

        assertThatThrownBy(() -> adapter.loadStats(LocalDateTime.of(2026, 8, 20, 10, 0)))
                .hasMessage("Dashboard owner query unavailable");
    }

    private DefaultAdminDashboardReadAdapter adapter() {
        DefaultAdminDashboardReadAdapter adapter =
                new DefaultAdminDashboardReadAdapter(submissionAdminReadPort);
        ReflectionTestUtils.setField(adapter, "appDashboardReadPort", appDashboardReadPort);
        ReflectionTestUtils.setField(adapter, "accountQueryService", accountQueryService);
        return adapter;
    }
}
