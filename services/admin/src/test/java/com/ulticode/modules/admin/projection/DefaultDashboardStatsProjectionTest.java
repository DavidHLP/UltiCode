package com.ulticode.modules.admin.projection;

import com.ulticode.app.api.dto.DashboardAppStatsDTO;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    private DefaultDashboardStatsProjection projection;

    @BeforeEach
    void setUp() {
        projection = new DefaultDashboardStatsProjection(dashboardReadPort, CLOCK);
        when(dashboardReadPort.loadStats(any(LocalDateTime.class))).thenReturn(emptyDashboardData());
    }

    @Test
    @DisplayName("loadStats correctly shapes all 7 dashboard stat blocks")
    void loadStats_shapesAllStatBlocks() {
        when(dashboardReadPort.loadStats(any(LocalDateTime.class))).thenReturn(
                new AdminDashboardReadPort.DashboardData(
                        new AdminDashboardReadPort.DashboardUserData(
                                3, 2, 1, 2, 2, 2, Map.of("ADMIN", 1L, "USER", 2L)),
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
        when(dashboardReadPort.loadChartData(eq("users"), any(LocalDateTime.class),
                any(LocalDateTime.class), eq("day")))
                .thenReturn(List.of(new AdminDashboardReadPort.ChartPoint("2026-07-28", 1L)));

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
        when(dashboardReadPort.loadChartData(eq("users"), any(LocalDateTime.class),
                any(LocalDateTime.class), eq("day")))
                .thenReturn(List.of(new AdminDashboardReadPort.ChartPoint("2026-07-27", 1L)));

        ChartStatsVO chart = projection.loadChartStats("users", "day", 7);

        assertThat(chart.getData()).singleElement()
                .satisfies(point -> {
                    assertThat(point.getDate()).isEqualTo("2026-07-27");
                    assertThat(point.getCount()).isEqualTo(1L);
                });
        verify(dashboardReadPort).loadChartData(eq("users"), any(LocalDateTime.class),
                any(LocalDateTime.class), eq("day"));
    }

    @Test
    @DisplayName("loadChartStats returns empty user data for an empty Auth page")
    void loadChartStats_returnsEmptyForNoAccounts() {
        when(dashboardReadPort.loadChartData(eq("users"), any(LocalDateTime.class),
                any(LocalDateTime.class), eq("day"))).thenReturn(List.of());

        ChartStatsVO chart = projection.loadChartStats("users", "day", 7);

        assertThat(chart.getData()).isEmpty();
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
