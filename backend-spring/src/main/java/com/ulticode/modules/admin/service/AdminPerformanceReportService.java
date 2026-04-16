package com.ulticode.modules.admin.service;

import com.ulticode.modules.admin.dto.PerformanceReportVO;

/**
 * Service interface for system performance reporting.
 * Provides JVM metrics and resource usage data.
 */
public interface AdminPerformanceReportService {

    /**
     * Get performance report.
     *
     * @return performance report with system metrics and resource usage
     */
    PerformanceReportVO getPerformanceReport();
}
