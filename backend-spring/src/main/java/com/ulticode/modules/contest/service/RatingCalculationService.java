package com.ulticode.modules.contest.service;

/**
 * Service for calculating Codeforces-style Elo ratings after contest completion.
 */
public interface RatingCalculationService {

    /**
     * Calculate and update ratings for all participants in a contest.
     * Updates global_rankings table and contest_participants.final_rank.
     *
     * @param contestId the contest ID
     */
    void calculateAndUpdate(String contestId);
}
