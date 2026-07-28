package com.ulticode.modules.admin.projection;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultDashboardStatsProjectionTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-07-28T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_INSTANT, ZoneId.of("UTC"));

    @Mock
    private DashboardMapper dashboardMapper;

    private DefaultDashboardStatsProjection projection;

    @BeforeEach
    void setUp() {
        projection = new DefaultDashboardStatsProjection(dashboardMapper, CLOCK);
    }

    @Test
    @DisplayName("loadStats correctly shapes all 7 dashboard stat blocks")
    void loadStats_shapesAllStatBlocks() {
        when(dashboardMapper.countTotalUsers()).thenReturn(100L);
        when(dashboardMapper.countActiveUsers()).thenReturn(80L);
        when(dashboardMapper.countBannedUsers()).thenReturn(5L);
        when(dashboardMapper.countActiveUsersSince(any(LocalDateTime.class))).thenReturn(20L);
        when(dashboardMapper.countUsersByRoleRaw()).thenReturn(List.of(Map.of("role", "ADMIN", "count", 2L)));

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
        when(dashboardMapper.countFlaggedPosts()).thenReturn(2L);
        when(dashboardMapper.countFlaggedComments()).thenReturn(3L);

        DashboardStatsVO stats = projection.loadStats();

        assertThat(stats).isNotNull();
        assertThat(stats.getUsers().getTotal()).isEqualTo(100L);
        assertThat(stats.getUsers().getActive()).isEqualTo(80L);
        assertThat(stats.getUsers().getBanned()).isEqualTo(5L);

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
    @DisplayName("loadChartStats returns points shaped from raw query data")
    void loadChartStats_shapesChartData() {
        when(dashboardMapper.getUsersChartData(any(), any(), anyString()))
                .thenReturn(List.of(Map.of("date", "2026-07-28", "count", 5L)));

        ChartStatsVO chart = projection.loadChartStats("users", "7d", 7);

        assertThat(chart).isNotNull();
        assertThat(chart.getMetric()).isEqualTo("users");
        assertThat(chart.getData()).hasSize(1);
        assertThat(chart.getData().get(0).getDate()).isEqualTo("2026-07-28");
        assertThat(chart.getData().get(0).getCount()).isEqualTo(5L);
    }
}
