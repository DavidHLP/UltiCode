package com.ulticode.app.api.service;

/**
 * Port through which the submission module triggers achievement checks
 * after a verdict, decoupling it from the achievement module.
 *
 * <p>The achievement module supplies the only production implementation.
 */
public interface AchievementTriggerPort {

    /**
     * Trigger achievement evaluation for a user's submission.
     * Fire-and-forget: failures are logged but never propagate.
     *
     * @param userId       the user id
     * @param problemId    the problem id
     * @param accepted     whether the submission was accepted
     * @param submissionId the submission id
     */
    void triggerOnSubmission(String userId, Long problemId, boolean accepted, String submissionId);
}
