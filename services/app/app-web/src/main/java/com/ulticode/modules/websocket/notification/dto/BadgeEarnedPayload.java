package com.ulticode.modules.websocket.notification.dto;

import java.time.Instant;

/**
 * Badge earned payload for achievement notifications.
 *
 * <p>Sent when a user earns a badge/achievement.
 */
public record BadgeEarnedPayload(
    String event,
    String badgeId,
    String badgeName,
    String badgeDescription,
    String badgeIcon,
    String badgeTier,
    String userId,
    Instant earnedAt) {

  /** Badge tiers. */
  public static class BadgeTier {
    public static final String BRONZE = "bronze";
    public static final String SILVER = "silver";
    public static final String GOLD = "gold";
    public static final String PLATINUM = "platinum";

    private BadgeTier() {}
  }

  /**
   * Create a badge earned payload.
   *
   * @param badgeId the badge ID
   * @param badgeName the badge name
   * @param badgeDescription the badge description
   * @param badgeIcon the badge icon URL
   * @param badgeTier the badge tier
   * @param userId the user ID who earned the badge
   * @return badge earned payload
   */
  public static BadgeEarnedPayload of(
      String badgeId,
      String badgeName,
      String badgeDescription,
      String badgeIcon,
      String badgeTier,
      String userId) {
    return new BadgeEarnedPayload(
        "badge_earned",
        badgeId,
        badgeName,
        badgeDescription,
        badgeIcon,
        badgeTier,
        userId,
        Instant.now());
  }

  /**
   * Create a bronze badge earned payload.
   *
   * @param badgeId the badge ID
   * @param badgeName the badge name
   * @param badgeDescription the badge description
   * @param badgeIcon the badge icon URL
   * @param userId the user ID who earned the badge
   * @return badge earned payload
   */
  public static BadgeEarnedPayload bronze(
      String badgeId,
      String badgeName,
      String badgeDescription,
      String badgeIcon,
      String userId) {
    return of(badgeId, badgeName, badgeDescription, badgeIcon, BadgeTier.BRONZE, userId);
  }

  /**
   * Create a silver badge earned payload.
   *
   * @param badgeId the badge ID
   * @param badgeName the badge name
   * @param badgeDescription the badge description
   * @param badgeIcon the badge icon URL
   * @param userId the user ID who earned the badge
   * @return badge earned payload
   */
  public static BadgeEarnedPayload silver(
      String badgeId,
      String badgeName,
      String badgeDescription,
      String badgeIcon,
      String userId) {
    return of(badgeId, badgeName, badgeDescription, badgeIcon, BadgeTier.SILVER, userId);
  }

  /**
   * Create a gold badge earned payload.
   *
   * @param badgeId the badge ID
   * @param badgeName the badge name
   * @param badgeDescription the badge description
   * @param badgeIcon the badge icon URL
   * @param userId the user ID who earned the badge
   * @return badge earned payload
   */
  public static BadgeEarnedPayload gold(
      String badgeId,
      String badgeName,
      String badgeDescription,
      String badgeIcon,
      String userId) {
    return of(badgeId, badgeName, badgeDescription, badgeIcon, BadgeTier.GOLD, userId);
  }
}
