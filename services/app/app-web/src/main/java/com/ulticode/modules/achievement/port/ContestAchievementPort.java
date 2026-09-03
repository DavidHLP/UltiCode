package com.ulticode.modules.achievement.port;

/**
 * Contest-specific achievement trigger port consumed by backend-app contest
 * service after the family relocated from backend-legacy.
 *
 * <p>This port covers the contest-participation milestone trigger that contest
 * code fires when a user registers. Submission verdict triggers stay inside
 * the achievement consumer and do not need a second public App contract.
 *
 * <p>P7-RELOCATE-CONTEST-001: replaces direct
 * {@code com.ulticode.modules.achievement.service.AchievementTriggerService}
 * import.
 *
 * @author ulticode
 */
public interface ContestAchievementPort {

    /**
     * Fire the contest-participation achievement milestone.
     *
     * @param userId            the participant user id
     * @param participationCount total contest participations by this user
     */
    void triggerContestParticipation(String userId, int participationCount);
}
