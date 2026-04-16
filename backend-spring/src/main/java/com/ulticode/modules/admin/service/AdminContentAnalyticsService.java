package com.ulticode.modules.admin.service;

import com.ulticode.modules.admin.dto.ProblemCompletionReportVO;

/**
 * Service interface for content analytics operations.
 * Provides problem completion statistics and content engagement data.
 */
public interface AdminContentAnalyticsService {

    /**
     * Get problem completion report.
     *
     * @param days number of days to analyze (default: 30)
     * @return problem completion report with statistics by difficulty and tags
     */
    ProblemCompletionReportVO getProblemCompletionReport(Integer days);
}
