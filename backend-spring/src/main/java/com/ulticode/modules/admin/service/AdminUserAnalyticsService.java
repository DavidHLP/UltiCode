package com.ulticode.modules.admin.service;

import com.ulticode.modules.admin.dto.UserActivityReportVO;

/**
 * Service interface for user analytics operations.
 * Provides user activity, retention, and engagement data.
 */
public interface AdminUserAnalyticsService {

    /**
     * Get user activity report.
     *
     * @param days number of days to analyze (default: 30)
     * @return user activity report with daily active users, retention, etc.
     */
    UserActivityReportVO getUserActivityReport(Integer days);
}
