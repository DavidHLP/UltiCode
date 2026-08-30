package com.ulticode.modules.achievement.service;

import com.ulticode.modules.achievement.constants.AchievementType;

import java.util.List;

/**
 * Intake seam for "an achievement check was requested for a user".
 *
 * <p>Owns exactly two responsibilities:
 * <ol>
 *   <li>{@link #trigger(String, AchievementType, int)} — fire-and-forget. Publishes
 *       an {@code AchievementCheckEvent} that {@link
 *       com.ulticode.modules.achievement.listener.AchievementCheckListener}
 *       picks up after the publishing transaction commits.</li>
 *   <li>{@link #checkAndAwardAchievements(String, AchievementType, int)} —
 *       synchronous check, run by the listener on a worker thread. Returns
 *       the IDs of newly-awarded achievements.</li>
 * </ol>
 *
 * <p><strong>Shallow-service collapse (architecture review 2026-07-08).</strong>
 * The previous interface carried 11 near-identical shim methods
 * (<code>onProblemSolved</code>, <code>onSubmissionMade</code>, ...,
 * <code>onLanguageMilestone</code>), one per {@link AchievementType}. Every
 * shim was a 1-line event publish — the deletion test forced the collapse
 * to a single typed method. The call site now names the {@code AchievementType}
 * explicitly, which is more honest: the caller already knows the type.
 *
 * <p>Callers from other modules:
 * <ul>
 *   <li><code>SubmissionJudgedInboxBridge</code> — problem solved, first problem, language milestone</li>
 *   <li><code>ContestServiceImpl</code> — contest joined</li>
 *   <li><code>FollowServiceImpl</code> — follower count changed</li>
 * </ul>
 */
public interface AchievementTriggerService {

    /**
     * Fire-and-forget trigger: ask the achievement module to check whether
     * the user has earned any achievement of the given type at the current
     * value. Publishes an {@code AchievementCheckEvent} consumed by
     * {@link com.ulticode.modules.achievement.listener.AchievementCheckListener}
     * after the publishing transaction commits.
     *
     * @param userId       the user to evaluate
     * @param type         which achievement family to evaluate
     * @param currentValue the current value (problems solved, contests joined, etc.)
     */
    void trigger(String userId, AchievementType type, int currentValue);

    /**
     * Synchronous check & award. Called by
     * {@link com.ulticode.modules.achievement.listener.AchievementCheckListener}
     * on a worker thread after the trigger event arrives. Awarded achievements
     * are persisted, badge push is fired, and {@code AchievementEarnedEvent}
     * is published.
     *
     * @param userId       the user to evaluate
     * @param type         which achievement family to evaluate
     * @param currentValue the current value (problems solved, contests joined, etc.)
     * @return the IDs of newly-awarded achievements (empty if none)
     */
    List<String> checkAndAwardAchievements(String userId, AchievementType type, int currentValue);
}
