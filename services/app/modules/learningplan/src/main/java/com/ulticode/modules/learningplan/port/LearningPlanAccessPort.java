package com.ulticode.modules.learningplan.port;

/**
 * Consumer-owned boundary the learning-plan domain needs from other owners.
 *
 * <p>Implemented in the boot shell by adapters that call the Auth identity RPC
 * and the Submission owner read contract. Both methods must fail closed:
 * a missing/degraded dependency must reject the request, never silently
 * permit the write. Calls happen <em>before</em> the local transaction —
 * no cross-owner RPC is performed while holding the DB transaction.
 */
public interface LearningPlanAccessPort {

    /**
     * Verify the principal is a live, non-banned account that matches the id.
     *
     * @throws com.ulticode.common.exception.BusinessException when the identity
     *         cannot be established (unavailable, incomplete, inactive or
     *         mismatched)
     */
    void requireActiveUser(String userId);

    /**
     * Verify the current user can see the source submission.
     *
     * @throws com.ulticode.common.exception.BusinessException when the submission
     *         is missing, not owned by the user, or the ownership check is
     *         unavailable
     */
    void requireOwnedSubmission(String userId, String submissionId);
}
