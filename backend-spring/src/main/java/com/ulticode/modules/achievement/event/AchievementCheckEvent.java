package com.ulticode.modules.achievement.event;

import com.ulticode.modules.achievement.constants.AchievementType;

/**
 * Event published by trigger methods to request async achievement check.
 * Consumed by AchievementCheckListener after the main transaction commits.
 */
public record AchievementCheckEvent(
    String userId,
    AchievementType type,
    int currentValue
) {}
