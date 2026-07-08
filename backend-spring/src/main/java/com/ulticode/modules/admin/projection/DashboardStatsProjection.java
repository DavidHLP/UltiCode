package com.ulticode.modules.admin.projection;

import com.ulticode.modules.admin.dto.ChartStatsVO;
import com.ulticode.modules.admin.dto.DashboardStatsVO;

/**
 * Read-side deep module owning dashboard statistics aggregation and chart-data shaping.
 *
 * <p>Mirrors the ADR-0011 {@code *Projection} pattern: the interface is the test surface,
 * the implementation absorbs entity&rarr;VO shaping + cross-mapper enrichment that previously
 * lived in {@code DashboardServiceImpl} (7 private sub-aggregators) and the {@code default}
 * methods on {@code DashboardMapper}.
 *
 * <p>The mapper keeps only raw {@code @Select} queries; this projection owns the Java-side
 * shape rule and is unit-testable in pure JVM without a database.
 */
public interface DashboardStatsProjection {

    /**
     * Load all dashboard statistics blocks (users, problems, contests, submissions,
     * solutions, forum, system) in one call.
     *
     * @return the fully populated stats VO
     */
    DashboardStatsVO loadStats();

    /**
     * Load chart data for a specific metric over a time period.
     *
     * @param metric one of users, submissions, contests, problems, solutions, forum_posts
     * @param period one of hour, day, week, month, year
     * @param days   optional override for the look-back window; null/0 uses period default
     * @return the chart stats VO with data points
     */
    ChartStatsVO loadChartStats(String metric, String period, Integer days);
}
