package com.ulticode.modules.achievement.event;

import java.time.Instant;

/**
 * Event published when a user earns an achievement.
 */
public record AchievementEarnedEvent(
    String userId,
    String achievementId,
    String achievementKey,
    String achievementName,
    String achievementDescription,
    String achievementIcon,
    Integer achievementTier,
    Integer points,
    Instant earnedAt) {

  /**
   * Create an achievement earned event.
   *
   * @param userId the user ID
   * @param achievementId the achievement ID
   * @param achievementKey the achievement key
   * @param achievementName the achievement name
   * @param achievementDescription the achievement description
   * @param achievementIcon the achievement icon
   * @param achievementTier the achievement tier
   * @param points the points awarded
   * @return the event
   */
  public static AchievementEarnedEvent of(
      String userId,
      String achievementId,
      String achievementKey,
      String achievementName,
      String achievementDescription,
      String achievementIcon,
      Integer achievementTier,
      Integer points) {
    return new AchievementEarnedEvent(
        userId,
        achievementId,
        achievementKey,
        achievementName,
        achievementDescription,
        achievementIcon,
        achievementTier,
        points,
        Instant.now());
  }
}
