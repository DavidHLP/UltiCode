package com.ulticode.modules.user.projection;

import com.ulticode.modules.admin.dto.UserActivityReportVO;

/**
 * Read-side deep module that owns the user activity, retention, and engagement
 * aggregation. Lives in the user module because every input table
 * ({@code users} + {@code submissions}) is a user-domain concern; the cross-table
 * join is no longer scattered across an admin god-service.
 *
 * <p>Replaces the read body of the deleted
 * {@code com.ulticode.modules.admin.service.AdminUserAnalyticsService}. The
 * admin facade injects this projection and delegates the report assembly; the
 * DTO ({@code UserActivityReportVO}) stays in the admin module because the
 * admin analytics API shape is the API contract, not a user-domain value.
 *
 * <p>Why a separate module and not "a helper class" or "moved methods":
 * <ul>
 *   <li><b>Locality</b>: the DAU/weekly/hourly/retention math, the
 *       distinct-user aggregation, the top-active-user enrichment against
 *       {@code user} all live next to each other. They used to be one method
 *       in a 139-line admin service whose other responsibilities were
 *       unrelated (problem content analytics, performance metrics).</li>
 *   <li><b>Leverage</b>: any future user-domain endpoint that needs the same
 *       activity shape (per-user streaks, per-team DAU) reuses
 *       {@link #loadUserActivityReport(Integer)} directly instead of copying
 *       the aggregation.</li>
 *   <li><b>Interface is the test surface</b>: the join logic is testable
 *       with mocked mappers without standing up the admin analytics facade.</li>
 * </ul>
 *
 * <p>Dependency category: <b>in-process</b>. The default adapter is the only
 * implementation; an alternate adapter (e.g. a cached pre-aggregated read)
 * could substitute it later without touching callers.
 *
 * @author ulticode
 */
public interface UserActivityAnalyticsProjection {

    /**
     * Build the user activity report covering the last {@code days} days.
     * Falls back to a 30-day window when {@code days} is {@code null} or
     * non-positive.
     *
     * @param days number of days to analyze; {@code null} or non-positive
     *             defaults to 30
     * @return the populated activity report
     */
    UserActivityReportVO loadUserActivityReport(Integer days);
}
