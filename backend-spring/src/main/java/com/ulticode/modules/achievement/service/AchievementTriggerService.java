package com.ulticode.modules.achievement.service;

import com.ulticode.modules.achievement.constants.AchievementType;

/**
 * Service interface for achievement trigger operations.
 *
 * <p>Trigger methods are fire-and-forget: they publish an AchievementCheckEvent
 * and return immediately. Achievement checks run asynchronously after the
 * main transaction commits via AchievementCheckListener.
 */
public interface AchievementTriggerService {

    /**
     * Request async achievement check when a user solves a problem.
     * Fire-and-forget: publishes AchievementCheckEvent and returns immediately.
     */
    void onProblemSolved(String userId, int problemsSolvedCount);

    /**
     * Request async achievement check when a user makes a submission.
     * Fire-and-forget: publishes AchievementCheckEvent and returns immediately.
     */
    void onSubmissionMade(String userId, int submissionsCount);

    /**
     * Request async achievement check when a user joins a contest.
     * Fire-and-forget: publishes AchievementCheckEvent and returns immediately.
     */
    void onContestJoined(String userId, int contestParticipationCount);

    /**
     * Request async achievement check when a user wins a contest.
     * Fire-and-forget: publishes AchievementCheckEvent and returns immediately.
     */
    void onContestWon(String userId, int contestWinsCount);

    /**
     * Request async achievement check when a user places in a contest.
     * Fire-and-forget: publishes AchievementCheckEvent and returns immediately.
     */
    void onContestPlaced(String userId, int contestPlacedCount);

    /**
     * Request async achievement check when a user creates a forum post.
     * Fire-and-forget: publishes AchievementCheckEvent and returns immediately.
     */
    void onForumPostCreated(String userId, int forumPostsCount);

    /**
     * Request async achievement check when a user writes a solution.
     * Fire-and-forget: publishes AchievementCheckEvent and returns immediately.
     */
    void onSolutionWritten(String userId, int solutionsCount);

    /**
     * Request async achievement check for streak days.
     * Fire-and-forget: publishes AchievementCheckEvent and returns immediately.
     */
    void onStreakUpdated(String userId, int streakDays);

    /**
     * Request async achievement check for rating milestone.
     * Fire-and-forget: publishes AchievementCheckEvent and returns immediately.
     */
    void onRatingUpdated(String userId, int rating);

    /**
     * Request async achievement check when a user's follower count changes.
     * Fire-and-forget: publishes AchievementCheckEvent and returns immediately.
     */
    void onFollowCountUpdated(String userId, int followerCount);

    /**
     * Request async achievement check when a user solves their first problem.
     * Fire-and-forget: publishes AchievementCheckEvent and returns immediately.
     */
    void onFirstProblemSolved(String userId);

    /**
     * Request async achievement check when a user reaches a language milestone.
     * Fire-and-forget: publishes AchievementCheckEvent and returns immediately.
     */
    void onLanguageMilestone(String userId, String language, int count);

    /**
     * Check and award achievements for any achievement type.
     * Called by AchievementCheckListener after transaction commits.
     *
     * @param userId the user ID
     * @param type the achievement type
     * @param currentValue the current value for the type
     * @return list of achievement IDs that were awarded
     */
    java.util.List<String> checkAndAwardAchievements(String userId, AchievementType type, int currentValue);
}
