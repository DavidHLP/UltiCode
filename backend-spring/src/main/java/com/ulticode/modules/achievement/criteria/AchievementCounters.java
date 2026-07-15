package com.ulticode.modules.achievement.criteria;

/**
 * Read-side counters pre-fetched for achievement progress projection.
 *
 * <p>Immutable value object fed to {@link AchievementCriteria#currentValue(AchievementCounters)}.
 * Carries only the aggregate counts the criteria module knows how to interpret; the
 * {@code type}&rarr;counter mapping itself stays hidden inside {@link AchievementCriteria}.</p>
 *
 * @author UltiCode
 * @since 2026-07-15
 */
public record AchievementCounters(long problemsSolved, long submissionsMade) {
}
