package com.ulticode.modules.admin.port;

/**
 * Typed read port the admin module uses to aggregate per-user statistics
 * that live behind the submission and solution modules.
 *
 * <p>Replaces the direct dependency {@code AdminUserServiceImpl} used to have
 * on {@code SubmissionMapper} and {@code SolutionMapper}. The user-detail
 * page needs four cross-module counts (submissions, accepted problems,
 * solutions, submission streak) that are none of admin's business to query
 * by raw mapper. This port narrows that surface to four read methods; the
 * production adapter
 * ({@link com.ulticode.modules.admin.port.adapter.AdminUserStatsReadAdapter})
 * hides the two mappers and owns the {@literal null}→{@code 0} coercion.
 *
 * <p>Second phase of the AdminReadModel seam (after
 * {@link AdminSubmissionReadPort}). The interface returns primitives
 * ({@code long}/{@code int}) rather than {@code Long}/{@code Integer}: the
 * adapter guarantees non-null, so callers never re-implement null handling
 * — that is the leverage this deep module buys. The deletion test passes:
 * deleting the port would force {@code AdminUserServiceImpl.populateStats}
 * back into reaching across to two mappers plus four null guards.
 *
 * @author ulticode
 */
public interface AdminUserStatsReadPort {

    /**
     * Total submissions authored by the user.
     *
     * @param userId the user id
     * @return submission count, {@code 0} if the user has none
     */
    long countSubmissionsByUserId(String userId);

    /**
     * Distinct problems the user has at least one accepted submission for.
     *
     * @param userId the user id
     * @return accepted-problem count, {@code 0} if none
     */
    long countAcceptedProblemsByUserId(String userId);

    /**
     * Total published solutions authored by the user.
     *
     * @param userId the user id
     * @return solution count, {@code 0} if none
     */
    long countSolutionsByUserId(String userId);

    /**
     * Current consecutive-day submission streak for the user.
     *
     * @param userId the user id
     * @return streak in days, {@code 0} if none
     */
    int calculateSubmissionStreak(String userId);
}
