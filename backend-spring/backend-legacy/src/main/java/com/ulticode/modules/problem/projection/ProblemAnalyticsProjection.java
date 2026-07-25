package com.ulticode.modules.problem.projection;

import com.ulticode.modules.admin.dto.ProblemCompletionReportVO;

/**
 * Read-side deep module that owns problem-side content analytics. Lives in
 * the problem module because every input table ({@code problems} +
 * {@code problem_tags} + {@code problem_tag_relations} + {@code submissions})
 * is a problem-domain concern; the cross-table aggregation is no longer
 * scattered across an admin god-service.
 *
 * <p>Replaces the read body of the deleted
 * {@code com.ulticode.modules.admin.service.AdminContentAnalyticsService}.
 * The admin facade injects this projection and delegates the report assembly;
 * the DTO ({@code ProblemCompletionReportVO}) stays in the admin module
 * because the admin analytics API shape is the API contract, not a
 * problem-domain value.
 *
 * <p>Why a separate module and not "a helper class" or "moved methods":
 * <ul>
 *   <li><b>Locality</b>: the difficulty bucket aggregation, the tag-level
 *       join, the trending-problem lookup, and the hardest-problem scan
 *       all live next to each other. They used to be one method in a
 *       161-line admin service whose other responsibilities were unrelated
 *       (user activity, performance metrics).</li>
 *   <li><b>Leverage</b>: any future problem-domain endpoint that needs the
 *       same completion shape (per-problem stats, per-tag insights) reuses
 *       {@link #loadProblemCompletionReport(Integer)} directly instead of
 *       copying the aggregation.</li>
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
public interface ProblemAnalyticsProjection {

    /**
     * Build the problem completion report covering the last {@code days} days.
     * Falls back to a 30-day window when {@code days} is {@code null} or
     * non-positive.
     *
     * @param days number of days to analyze; {@code null} or non-positive
     *             defaults to 30
     * @return the populated completion report
     */
    ProblemCompletionReportVO loadProblemCompletionReport(Integer days);
}
