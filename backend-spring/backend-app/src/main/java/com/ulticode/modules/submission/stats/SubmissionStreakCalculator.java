package com.ulticode.modules.submission.stats;

/**
 * Deep module that owns the submission-streak computation for the submission
 * domain.
 *
 * <p>Replaces the previous shape where the streak logic was an in-line
 * {@code submissionMapper.calculateStreak(userId)} call (a recursive CTE)
 * sprinkled across three call sites ({@code DefaultSubmissionProjection},
 * {@code DefaultUserReadProjection}, {@code AdminUserStatsReadAdapter}).
 * Because the underlying query lives in SQL, callers had no way to test
 * their null-handling and coercion rules without booting MySQL — pinning
 * the contract here lets every caller mock a single method and verify
 * the null->{@code 0} coercion the original SQL {@code MIN(days_ago)}
 * already implied.
 *
 * <p>Why a separate module and not "a helper class":
 * <ul>
 *   <li><b>Locality</b>: the streak semantics (rolling window, gap reset,
 *       days-ago floor of 1) live entirely in {@link
 *       com.ulticode.modules.submission.mapper.SubmissionMapper#calculateStreak}
 *       and are easy to break in a query refactor. Concentrating the
 *       seam here means the contract that callers depend on is explicit
 *       and testable.</li>
 *   <li><b>Leverage</b>: the future achievement module / personal dashboard
 *       reuse this instead of re-importing {@code SubmissionMapper}.</li>
 *   <li><b>Interface is the test surface</b>: the math collapses to one
 *       mapper call + null coercion, so the only thing that ever needs
 *       exercising in a unit test is the delegation + null path.</li>
 * </ul>
 *
 * <p>Dependency category: <b>in-process</b> (single SQL read behind a
 * mockable collaborator). Mirrors the {@link SubmissionPerformanceStats}
 * shape: interface + single default adapter, no external seam.
 */
public interface SubmissionStreakCalculator {

    /**
     * Compute the user's current consecutive-day streak of submissions,
     * ending today (or yesterday — the SQL caps {@code days_ago <= 1}
     * so a gap of more than a day resets the count).
     *
     * <p>Returns {@code 0} for users with no accepted submissions in the
     * rolling 365-day window, mirroring the {@code MIN(days_ago)} semantics
     * of the underlying query and the legacy {@code streak != null ? streak : 0}
     * coercion performed by every prior caller.
     *
     * @param userId user ID (must be non-null and non-blank; callers are
     *               expected to have already loaded the user)
     * @return the current streak length, always {@code >= 0}
     */
    int computeStreak(String userId);
}
