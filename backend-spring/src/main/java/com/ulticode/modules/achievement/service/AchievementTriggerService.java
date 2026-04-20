package com.ulticode.modules.achievement.service;

import com.ulticode.modules.achievement.constants.AchievementType;

import java.util.List;

/**
 * Service interface for achievement trigger operations.
 *
 * <p>This service is responsible for checking and awarding achievements
 * when users perform certain actions.
 */
public interface AchievementTriggerService {

    /**
     * Check and award achievements when a user solves a problem.
     *
     * @param userId the user ID
     * @param problemsSolvedCount the current count of problems solved
     * @return list of achievement IDs that were awarded
     */
    List<String> onProblemSolved(String userId, int problemsSolvedCount);

    /**
     * Check and award achievements when a user makes a submission.
     *
     * @param userId the user ID
     * @param submissionsCount the current count of submissions made
     * @return list of achievement IDs that were awarded
     */
    List<String> onSubmissionMade(String userId, int submissionsCount);

    /**
     * Check and award achievements when a user joins a contest.
     *
     * @param userId the user ID
     * @param contestParticipationCount the current count of contests participated
     * @return list of achievement IDs that were awarded
     */
    List<String> onContestJoined(String userId, int contestParticipationCount);

    /**
     * Check and award achievements when a user wins a contest.
     *
     * @param userId the user ID
     * @param contestWinsCount the current count of contests won
     * @return list of achievement IDs that were awarded
     */
    List<String> onContestWon(String userId, int contestWinsCount);

    /**
     * Check and award achievements when a user places in a contest.
     *
     * @param userId the user ID
     * @param contestPlacedCount the current count of contest placements
     * @return list of achievement IDs that were awarded
     */
    List<String> onContestPlaced(String userId, int contestPlacedCount);

    /**
     * Check and award achievements when a user creates a forum post.
     *
     * @param userId the user ID
     * @param forumPostsCount the current count of forum posts
     * @return list of achievement IDs that were awarded
     */
    List<String> onForumPostCreated(String userId, int forumPostsCount);

    /**
     * Check and award achievements when a user writes a solution.
     *
     * @param userId the user ID
     * @param solutionsCount the current count of solutions written
     * @return list of achievement IDs that were awarded
     */
    List<String> onSolutionWritten(String userId, int solutionsCount);

    /**
     * Check and award achievements for streak days.
     *
     * @param userId the user ID
     * @param streakDays the current streak days
     * @return list of achievement IDs that were awarded
     */
    List<String> onStreakUpdated(String userId, int streakDays);

    /**
     * Check and award achievements for rating milestone.
     *
     * @param userId the user ID
     * @param rating the current rating
     * @return list of achievement IDs that were awarded
     */
    List<String> onRatingUpdated(String userId, int rating);

    /**
     * Check and award achievements when a user's follower count changes.
     *
     * @param userId the user ID
     * @param followerCount the current follower count
     * @return list of achievement IDs that were awarded
     */
    List<String> onFollowCountUpdated(String userId, int followerCount);

    /**
     * Check and award achievements for any achievement type.
     *
     * @param userId the user ID
     * @param type the achievement type
     * @param currentValue the current value for the type
     * @return list of achievement IDs that were awarded
     */
    List<String> checkAndAwardAchievements(String userId, AchievementType type, int currentValue);
}
