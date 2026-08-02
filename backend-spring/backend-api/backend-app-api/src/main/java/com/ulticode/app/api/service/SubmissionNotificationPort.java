package com.ulticode.app.api.service;

/**
 * Port through which the submission module dispatches submission-completed
 * notifications, decoupling it from the notification module.
 *
 * <p>The notification module supplies the production adapter.
 */
public interface SubmissionNotificationPort {

    /**
     * Dispatch a submission-completed notification.
     * Fire-and-forget: failures are logged but never propagate.
     *
     * @param submissionId the submission id
     * @param userId       the user id
     * @param problemId    the problem id
     * @param accepted     whether the submission was accepted
     * @param verdict      the verdict string
     */
    void dispatchSubmissionCompleted(String submissionId, String userId,
                                     Long problemId, boolean accepted, String verdict);
}
