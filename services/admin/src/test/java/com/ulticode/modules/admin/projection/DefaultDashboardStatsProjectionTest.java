package com.ulticode.modules.admin.projection;

import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.dto.ChartStatsVO;
import com.ulticode.modules.admin.dto.DashboardStatsVO;
import com.ulticode.modules.admin.mapper.DashboardMapper;
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
    private DashboardMapper dashboardMapper;

    @Mock
    private AccountQueryService accountQueryService;

    private DefaultDashboardStatsProjection projection;

    @BeforeEach
    void setUp() {
        projection = new DefaultDashboardStatsProjection(dashboardMapper, CLOCK, accountQueryService);
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
        when(accountQueryService.queryAccounts(any()))
                .thenReturn(RpcResult.page(List.of(admin, bannedUser, oldUser), 3L, 1, 100, "t-test"));

        when(dashboardMapper.countTotalProblems()).thenReturn(50L);
        when(dashboardMapper.countPublishedProblems()).thenReturn(40L);
        when(dashboardMapper.countProblemsByDifficultyRaw()).thenReturn(List.of(Map.of("difficulty", "EASY", "count", 30L)));
        when(dashboardMapper.countProblemsByStatusRaw()).thenReturn(List.of(Map.of("status", "PUBLISHED", "count", 40L)));

        when(dashboardMapper.countTotalContests()).thenReturn(10L);
        when(dashboardMapper.countUpcomingContests(any(LocalDateTime.class))).thenReturn(2L);
        when(dashboardMapper.countRunningContests(any(LocalDateTime.class))).thenReturn(1L);
        when(dashboardMapper.countFinishedContests(any(LocalDateTime.class))).thenReturn(7L);

        when(dashboardMapper.countTotalSubmissions()).thenReturn(500L);
        when(dashboardMapper.countSubmissionsSince(any(LocalDateTime.class))).thenReturn(50L);
        when(dashboardMapper.calculateAcceptanceRate()).thenReturn(65.5);
        when(dashboardMapper.countSubmissionsSince(any())).thenReturn(10L);

        when(dashboardMapper.countTotalSolutions()).thenReturn(30L);
        when(dashboardMapper.countPublishedSolutions()).thenReturn(25L);
        when(dashboardMapper.countFlaggedSolutions()).thenReturn(1L);

        when(dashboardMapper.countForumPosts()).thenReturn(80L);
        when(dashboardMapper.countForumComments()).thenReturn(200L);
        when(dashboardMapper.countForumCommunities()).thenReturn(5L);
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
}
