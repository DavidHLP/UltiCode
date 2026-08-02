package com.ulticode.app.api.service;

/**
 * Contest-specific achievement trigger port consumed by backend-app contest
 * service after the family relocated from backend-legacy.
 *
 * <p>The generic {@link AchievementTriggerPort} covers submission-side triggers;
 * this port covers the contest-participation milestone trigger that contest
 * code fires when a user registers.
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
