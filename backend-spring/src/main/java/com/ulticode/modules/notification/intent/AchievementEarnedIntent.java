package com.ulticode.modules.notification.intent;

import com.ulticode.modules.achievement.event.AchievementEarnedEvent;
import com.ulticode.modules.notification.entity.enums.NotificationCategory;

/**
 * Intent emitted when a user earns an achievement. The legacy listener
 * (pre-ADR-004) used raw string types and a non-enum category; this record
 * captures the same data with proper typing.
 *
 * <p>{@code tier} follows the existing convention in
 * {@code AchievementNotificationListener.getTierString} (1=Bronze, 2=Silver,
 * 3=Gold, 4=Platinum).
 *
 * <p>Reference: ADR-004 §2.1 (AchievementEarnedIntent).
 */
public record AchievementEarnedIntent(
        String userId,
        String achievementId,
        String achievementKey,
        String achievementName,
        String achievementDescription,
        String achievementIconUrl,
        Integer achievementTier,
        Integer points,
        NotificationCategory category
) implements NotificationIntent {

    @Override
    public String intentId() {
        // tier is not part of the key — a user can re-trigger the same
        // achievement (re-issue event) and we want the new delivery to be
        // collapsed under the existing intent id.
        return "achievement:" + userId + ":" + achievementId;
    }

    /**
     * Build from a {@link AchievementEarnedEvent}.
     */
    public static AchievementEarnedIntent of(AchievementEarnedEvent event) {
        return new AchievementEarnedIntent(
                event.userId(),
                event.achievementId(),
                event.achievementKey(),
                event.achievementName(),
                event.achievementDescription(),
                event.achievementIcon(),
                event.achievementTier(),
                event.points(),
                NotificationCategory.SYSTEM
        );
    }
}
