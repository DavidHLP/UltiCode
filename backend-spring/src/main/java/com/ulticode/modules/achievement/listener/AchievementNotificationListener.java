package com.ulticode.modules.achievement.listener;

import com.ulticode.modules.achievement.event.AchievementEarnedEvent;
import com.ulticode.modules.achievement.port.BadgePushPort;
import com.ulticode.modules.notification.service.NotificationDispatchService;
import com.ulticode.modules.notification.service.NotificationService;
import com.ulticode.modules.websocket.notification.dto.BadgeEarnedPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Async listener for achievement earned events.
 *
 * <p>Listens for AchievementEarnedEvent and creates a notification
 * when a user earns an achievement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementNotificationListener {

    private final NotificationService notificationService;
    private final NotificationDispatchService notificationDispatchService;
    private final BadgePushPort badgePushPort;
    /**
     * ADR-004 M4c: typed intent dispatcher. Active when
     * {@code app.features.use-notification-intent=true}.
     */
    private final com.ulticode.modules.notification.dispatcher.NotificationDispatcher notificationDispatcher;
    private final com.ulticode.common.config.FeatureFlagsProperties featureFlags;

    /**
     * Handle achievement earned events asynchronously.
     *
     * <p>ADR-004 M4c: when the flag is on, dispatch the typed
     * {@link com.ulticode.modules.notification.intent.AchievementEarnedIntent}
     * — the new {@link com.ulticode.modules.notification.channel.WebSocketNotificationChannel}
     * owns the WebSocket push, so the listener no longer calls
     * {@code realtimeService} directly. The legacy path keeps the manual WS
     * push for behavior parity.
     *
     * @param event the achievement earned event
     */
    @Async
    @EventListener
    public void onAchievementEarned(AchievementEarnedEvent event) {
        try {
            if (featureFlags.isUseNotificationIntent()) {
                // New path: dispatcher fans out to InApp + Email + WebSocket.
                // The WebSocket channel emits a BadgeEarnedPayload for the
                // frontend, so the manual realtimeService.sendNotification
                // call is intentionally removed here (avoid double-push).
                notificationDispatcher.dispatch(
                        com.ulticode.modules.notification.intent.AchievementEarnedIntent.of(event));
            } else {
                String tierStr = getTierString(event.achievementTier());

                // Legacy path: write the InApp row via the dispatch service
                // and push the WebSocket event inline. force=false because
                // the preference row falls through to "enabled" when
                // "badge_earned" is not a known category (Q20).
                notificationDispatchService.dispatch(
                        event.userId(),
                        "achievement",
                        "badge_earned",
                        "Achievement Earned: " + event.achievementName(),
                        event.achievementDescription() + " - " + tierStr + " badge, +" + event.points() + " points",
                        "/achievements",
                        null,
                        false
                );

                // Also push via WebSocket (per D-05) — best-effort, fire-and-forget.
                badgePushPort.pushBadgeEarned(event.userId(),
                    BadgeEarnedPayload.of(
                        event.achievementKey(),
                        event.achievementName(),
                        event.achievementDescription(),
                        null, // badgeIcon not available in event
                        getTierString(event.achievementTier()).toLowerCase(),
                        event.userId()
                    ));
            }

            log.debug("Created achievement notification for user {}: {}",
                    event.userId(), event.achievementKey());
        } catch (Exception e) {
            log.warn("Failed to create achievement notification for user {}: {}",
                    event.userId(), e.getMessage());
        }
    }

    private String getTierString(Integer tier) {
        if (tier == null) {
            return "Bronze";
        }
        return switch (tier) {
            case 1 -> "Bronze";
            case 2 -> "Silver";
            case 3 -> "Gold";
            case 4 -> "Platinum";
            default -> "Bronze";
        };
    }
}
