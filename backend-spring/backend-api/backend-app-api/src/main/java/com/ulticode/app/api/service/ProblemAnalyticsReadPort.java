package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.ProblemCompletionReportDTO;

/**
 * Read-side port for the admin Problem completion report.
 *
 * <p>The Problem provider owns aggregation and submission joins. The admin
 * edge maps the returned record to its HTTP response type. A null or
 * non-positive day window uses the provider's current default window, and a
 * report is always returned with zero values and empty lists when there are
 * no matching rows. Provider failures must not be converted into a false
 * successful empty report.
 */
public interface ProblemAnalyticsReadPort {

    /**
     * Load completion statistics for a recent time window.
     *
     * @param days number of days to analyze; null/non-positive uses the default
     * @return a non-null entity-free report
     */
    ProblemCompletionReportDTO loadProblemCompletionReport(Integer days);
}
