package com.ulticode.modules.admin.service;

import com.ulticode.modules.admin.dto.ChartStatsVO;
import com.ulticode.modules.admin.dto.DashboardStatsVO;

/**
 * Dashboard service interface.
 */
public interface DashboardService {

    /**
     * Get dashboard statistics.
     */
    DashboardStatsVO getStats();

    /**
     * Get chart statistics.
     *
     * @param metric the metric type (users, submissions, problems, contests, solutions, forum_posts)
     * @param period the period (hour, day, week, month, year)
     * @param days   number of days to look back
     */
    ChartStatsVO getChartStats(String metric, String period, Integer days);
}
