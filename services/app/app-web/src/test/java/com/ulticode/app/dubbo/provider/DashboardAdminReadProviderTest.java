package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.dto.DashboardAppStatsDTO;
import com.ulticode.app.api.dto.DashboardChartDataDTO;
import com.ulticode.modules.dashboard.mapper.DashboardAdminMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardAdminReadProviderTest {

    @Mock
    private DashboardAdminMapper mapper;

    @Test
    void loadDashboardStatsReturnsEntityFreeAppAggregates() {
        when(mapper.countTotalProblems()).thenReturn(5L);
        when(mapper.countPublishedProblems()).thenReturn(3L);
        when(mapper.countProblemsByDifficulty()).thenReturn(
                List.of(Map.of("bucket", "EASY", "count", 2L)));
        when(mapper.countProblemsByStatus()).thenReturn(
                List.of(Map.of("bucket", "PUBLISHED", "count", 3L)));

        DashboardAppStatsDTO result = new DashboardAdminReadProvider(mapper)
                .loadDashboardStats(LocalDateTime.of(2026, 8, 20, 10, 0));

        assertThat(result.totalProblems()).isEqualTo(5L);
        assertThat(result.publishedProblems()).isEqualTo(3L);
        assertThat(result.problemsByDifficulty())
                .containsExactly(new DashboardAppStatsDTO.Count("EASY", 2L));
        assertThat(result.problemsByStatus())
                .containsExactly(new DashboardAppStatsDTO.Count("PUBLISHED", 3L));
    }

    @Test
    void loadChartDataWhitelistsMetricAndPeriodFormat() {
        when(mapper.chartProblems(
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 20, 0, 0),
                "%Y-%u"))
                .thenReturn(List.of(Map.of("bucket", "2026-33", "count", 7L)));

        List<DashboardChartDataDTO> result = new DashboardAdminReadProvider(mapper)
                .loadDashboardChartData(
                        "problems",
                        LocalDateTime.of(2026, 8, 1, 0, 0),
                        LocalDateTime.of(2026, 8, 20, 0, 0),
                        "week");

        assertThat(result).containsExactly(new DashboardChartDataDTO("2026-33", 7L));
    }
}
