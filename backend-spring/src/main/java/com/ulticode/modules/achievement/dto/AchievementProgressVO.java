package com.ulticode.modules.achievement.dto;

/**
 * View object representing a user's progress on an achievement.
 */
public record AchievementProgressVO(
    String achievementId,
    String key,
    String name,
    String icon,
    Integer tier,
    String category,
    Integer currentValue,
    Integer targetValue,
    Integer percentage,
    String nextMilestone
) {}
