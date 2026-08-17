package com.ulticode.modules.notification.intent;

import com.ulticode.modules.achievement.event.AchievementEarnedEvent;
import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.notification.api.dto.NotificationPayload;

import java.time.Instant;

/**
 * Intent emitted when a user earns an achievement. The legacy listener
 * (pre-ADR-004) used raw string types and a non-enum category; this record
 * captures the same data with proper typing.
 *
 * <p>{@code tier} follows the existing convention in
 * {@code AchievementNotificationListener.getTierString} (1=Bronze, 2=Silver,
 * 3=Gold, 4=Platinum).
 *
 * <p>{@code earnedAt} is part of the natural key (see {@link #intentId()})
 * so a re-issued event (tier-up promotion, system re-trigger, etc.)
 * produces a distinct ledger row and re-fans out to all channels.
 *
 * <p>Unlike the other intents, the achievement channel pushes a typed
 * {@code BadgeEarnedPayload} via {@code BadgePushPort}, not the generic
 * {@link NotificationPayload}. {@link #toPushPayload()} therefore throws —
 * the channel detects this intent with a single {@code instanceof} and routes
 * to the achievement push port. The two-channel split is the one remaining
 * special case in {@code WebSocketNotificationChannel}, and the right call:
 * the achievement payload is a genuinely different DTO consumed by a
 * different frontend handler.
 *
 * <p>Reference: ADR-004 §2.1 (AchievementEarnedIntent); M4d-1 review
 * finding #6.
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
        Instant earnedAt,
        NotificationCategory category
) implements NotificationIntent {

    @Override
    public String intentId() {
        // ADR-004 M4d-1 finding #6: earnedAt is part of the key so re-issued
        // achievement events (tier-up promotion, system re-evaluate) produce
        // a distinct ledger row. Trade-off: a true duplicate event within
        // the same millisecond is still collapsed — acceptable since
        // AchievementEarnedEvent is published once per earn.
        long ts = earnedAt == null ? 0L : earnedAt.toEpochMilli();
        return "achievement:" + userId + ":" + achievementId + ":at" + ts;
    }

    @Override
    public String wireType() {
        return "ACHIEVEMENT";
    }

    @Override
    public NotificationPayload toPushPayload() {
        throw new UnsupportedOperationException(
                "AchievementEarnedIntent must be pushed via BadgePushPort as BadgeEarnedPayload, "
                        + "not via NotificationPushPort; the channel handles this with a single instanceof");
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
                event.earnedAt(),
                NotificationCategory.SYSTEM
        );
    }
}
