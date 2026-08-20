package com.ulticode.modules.admin.projection;

import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.app.api.dto.DashboardAppStatsDTO;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.dto.ChartStatsVO;
import com.ulticode.modules.admin.dto.DashboardStatsVO;
import com.ulticode.modules.admin.port.AdminDashboardReadPort;
import com.ulticode.submission.api.dto.SubmissionDashboardStatsDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultDashboardStatsProjectionTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-07-28T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_INSTANT, ZoneId.of("UTC"));

    @Mock
    private AdminDashboardReadPort dashboardReadPort;

    @Mock
    private AccountQueryService accountQueryService;

    private DefaultDashboardStatsProjection projection;

    @BeforeEach
    void setUp() {
        projection = new DefaultDashboardStatsProjection(dashboardReadPort, CLOCK, accountQueryService);
        when(dashboardReadPort.loadStats(any(LocalDateTime.class))).thenReturn(emptyDashboardData());
    }

    @Test
    @DisplayName("loadStats correctly shapes all 7 dashboard stat blocks")
    void loadStats_shapesAllStatBlocks() {
        AuthAccountDTO admin = account("admin", "ADMIN", true, false,
                FIXED_INSTANT.atZone(ZoneId.of("UTC")).toLocalDateTime().minusHours(1),
                FIXED_INSTANT.atZone(ZoneId.of("UTC")).toLocalDateTime().minusHours(1));
        AuthAccountDTO bannedUser = account("banned", "USER", true, true,
                LocalDateTime.of(2026, 7, 20, 9, 0),
                LocalDateTime.of(2026, 7, 27, 11, 0));
        AuthAccountDTO oldUser = account("old", "USER", false, false,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 1, 9, 0));
        when(accountQueryService.getDashboardStatsSummary())
                .thenReturn(RpcResult.success(new AccountQueryService.AccountStatsSummary(
                        3, 2, 1, 2, 2, 2, Map.of("ADMIN", 1L, "USER", 2L)), "t-test"));

        when(dashboardReadPort.loadStats(any(LocalDateTime.class))).thenReturn(
                new AdminDashboardReadPort.DashboardData(
                        new DashboardAppStatsDTO(
                                50, 40,
                                List.of(new DashboardAppStatsDTO.Count("EASY", 30)),
                                List.of(new DashboardAppStatsDTO.Count("PUBLISHED", 40)),
                                10, 2, 1, 7,
                                30, 25, 1,
                                80, 200, 5, 0, 0),
                        new SubmissionDashboardStatsDTO(500, 50, 50, 50, 65.5)));
        DashboardStatsVO stats = projection.loadStats();

        assertThat(stats).isNotNull();
        assertThat(stats.getUsers().getTotal()).isEqualTo(3L);
        assertThat(stats.getUsers().getActive()).isEqualTo(2L);
        assertThat(stats.getUsers().getBanned()).isEqualTo(1L);
        assertThat(stats.getUsers().getActiveToday()).isEqualTo(2L);
        assertThat(stats.getUsers().getActiveWeek()).isEqualTo(2L);
        assertThat(stats.getUsers().getActiveMonth()).isEqualTo(2L);
        assertThat(stats.getUsers().getByRole()).containsEntry("ADMIN", 1L)
                .containsEntry("USER", 2L);

        assertThat(stats.getProblems().getTotal()).isEqualTo(50L);
        assertThat(stats.getProblems().getPublished()).isEqualTo(40L);
        assertThat(stats.getProblems().getUnpublished()).isEqualTo(10L);

        assertThat(stats.getContests().getTotal()).isEqualTo(10L);
        assertThat(stats.getContests().getUpcoming()).isEqualTo(2L);
        assertThat(stats.getContests().getRunning()).isEqualTo(1L);
        assertThat(stats.getContests().getFinished()).isEqualTo(7L);

        assertThat(stats.getSubmissions().getTotal()).isEqualTo(500L);
        assertThat(stats.getSubmissions().getAcceptanceRate()).isEqualTo(65.5);

        assertThat(stats.getSolutions().getTotal()).isEqualTo(30L);
        assertThat(stats.getSolutions().getPublished()).isEqualTo(25L);

        assertThat(stats.getForum().getPosts()).isEqualTo(80L);
        assertThat(stats.getForum().getComments()).isEqualTo(200L);
        assertThat(stats.getSystem().getUptime()).isNotNull();
    }

    @Test
    @DisplayName("loadChartStats routes owner metrics through one dashboard read seam")
    void loadChartStats_routesOwnerMetricThroughReadSeam() {
        when(dashboardReadPort.loadChartData(org.mockito.ArgumentMatchers.eq("problems"), any(LocalDateTime.class),
                any(LocalDateTime.class), org.mockito.ArgumentMatchers.eq("day")))
                .thenReturn(List.of(new AdminDashboardReadPort.ChartPoint("2026-07-28", 4L)));

        ChartStatsVO chart = projection.loadChartStats("problems", "day", 7);

        assertThat(chart.getData()).singleElement().satisfies(point -> {
            assertThat(point.getDate()).isEqualTo("2026-07-28");
            assertThat(point.getCount()).isEqualTo(4L);
        });
        verify(dashboardReadPort).loadChartData("problems", chart.getStartDate(),
                chart.getEndDate(), "day");
    }

    @Test
    @DisplayName("loadChartStats shapes Auth account data")
    void loadChartStats_shapesChartData() {
        AuthAccountDTO recent = account("recent", "USER", true, false,
                LocalDateTime.of(2026, 7, 28, 9, 0),
                LocalDateTime.of(2026, 7, 28, 9, 0));
        when(accountQueryService.queryAccounts(any()))
                .thenReturn(RpcResult.page(List.of(recent), 1L, 1, 100, "t-test"));

        ChartStatsVO chart = projection.loadChartStats("users", "day", 7);

        assertThat(chart).isNotNull();
        assertThat(chart.getMetric()).isEqualTo("users");
        assertThat(chart.getData()).hasSize(1);
        assertThat(chart.getData().get(0).getDate()).isEqualTo("2026-07-28");
        assertThat(chart.getData().get(0).getCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("loadChartStats paginates joined-at records and stops at the window")
    void loadChartStats_scansPagesAndStopsAtWindow() {
        AuthAccountDTO recent = account("recent", "USER", true, false,
                LocalDateTime.of(2026, 7, 27, 9, 0),
                LocalDateTime.of(2026, 7, 27, 9, 0));
        AuthAccountDTO old = account("old", "USER", true, false,
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 1, 9, 0));
        when(accountQueryService.queryAccounts(any()))
                .thenReturn(
                        RpcResult.page(List.of(recent), 2L, 1, 1, "t-test"),
                        RpcResult.page(List.of(old), 2L, 2, 1, "t-test"));

        ChartStatsVO chart = projection.loadChartStats("users", "day", 7);

        assertThat(chart.getData()).singleElement()
                .satisfies(point -> {
                    assertThat(point.getDate()).isEqualTo("2026-07-27");
                    assertThat(point.getCount()).isEqualTo(1L);
                });
        verify(accountQueryService, times(2)).queryAccounts(any());
    }

    @Test
    @DisplayName("loadChartStats returns empty user data for an empty Auth page")
    void loadChartStats_returnsEmptyForNoAccounts() {
        when(accountQueryService.queryAccounts(any()))
                .thenReturn(RpcResult.page(List.of(), 0L, 1, 100, "t-test"));

        ChartStatsVO chart = projection.loadChartStats("users", "day", 7);

        assertThat(chart.getData()).isEmpty();
    }

    @Test
    @DisplayName("loadChartStats fails closed when Auth is unavailable")
    void loadChartStats_failsClosedOnAuthFailure() {
        when(accountQueryService.queryAccounts(any()))
                .thenReturn(RpcResult.failure(AuthErrorCode.UNEXPECTED_AUTH_STATE, "t-test"));

        assertThatThrownBy(() -> projection.loadChartStats("users", "day", 7))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Auth account owner unavailable");
    }

    @Test
    @DisplayName("loadStats fails closed on inconsistent Auth pagination metadata")
    void loadStats_failsClosedOnInconsistentPagination() {
        AuthAccountDTO account = account("account", "USER", true, false,
                LocalDateTime.of(2026, 7, 28, 9, 0),
                LocalDateTime.of(2026, 7, 28, 9, 0));
        when(accountQueryService.queryAccounts(any()))
                .thenReturn(RpcResult.page(List.of(account), 3L, 1, 100, "t-test"));

        assertThatThrownBy(projection::loadStats)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Auth account owner unavailable");
    }

    private static AuthAccountDTO account(String id, String role, boolean active, boolean banned,
                                          LocalDateTime joinedAt, LocalDateTime lastLoginAt) {
        return new AuthAccountDTO(id, id, id + "@example.com", role, active, banned,
                null, null, joinedAt, lastLoginAt, 1L);
    }

    private static AdminDashboardReadPort.DashboardData emptyDashboardData() {
        return new AdminDashboardReadPort.DashboardData(
                new DashboardAppStatsDTO(
                        0, 0, List.of(), List.of(),
                        0, 0, 0, 0,
                        0, 0, 0,
                        0, 0, 0, 0, 0),
                new SubmissionDashboardStatsDTO(0, 0, 0, 0, 0.0));
    }
}
