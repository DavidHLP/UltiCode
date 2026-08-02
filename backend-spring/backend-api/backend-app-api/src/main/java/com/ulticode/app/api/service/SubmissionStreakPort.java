package com.ulticode.app.api.service;

/**
 * Port through which the user module computes submission streaks
 * without importing the submission module directly.
 *
 * <p>P7-RELOCATE-SUBMISSION-001: extracted when SubmissionStreakCalculator
 * relocated to backend-app.
 */
public interface SubmissionStreakPort {

    /**
     * Compute the user's current consecutive-day streak of submissions.
     *
     * @param userId user ID
     * @return current streak length, always >= 0
     */
    int computeStreak(String userId);
}
