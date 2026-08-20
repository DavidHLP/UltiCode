package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.DashboardAppStatsDTO;
import com.ulticode.app.api.dto.DashboardChartDataDTO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * App-owner read seam for the Admin Dashboard's App-owned aggregates.
 *
 * <p>The contract returns only entity-free dashboard data. The App provider
 * owns all SQL against problems, contests, solutions and forum tables; Admin
 * never imports those mappers or tables.</p>
 */
public interface DashboardAdminReadPort {

    /** Load the current App-owned dashboard aggregates at the supplied time. */
    DashboardAppStatsDTO loadDashboardStats(LocalDateTime now);

    /**
     * Load bounded chart buckets for an App-owned metric.
     *
     * @param metric one of {@code problems}, {@code contests},
     *               {@code solutions}, or {@code forum_posts}
     * @param start inclusive lower bound
     * @param end inclusive upper bound
     * @param period one of {@code hour}, {@code day}, {@code week},
     *               {@code month}, or {@code year}
     */
    List<DashboardChartDataDTO> loadDashboardChartData(
            String metric, LocalDateTime start, LocalDateTime end, String period);
}
