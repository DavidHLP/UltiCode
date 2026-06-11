package com.ulticode.modules.achievement.listener;

import com.ulticode.modules.achievement.event.AchievementEarnedEvent;
import com.ulticode.modules.notification.service.NotificationDispatchService;
import com.ulticode.modules.notification.service.NotificationService;
import com.ulticode.modules.websocket.notification.dto.BadgeEarnedPayload;
import com.ulticode.modules.websocket.service.RealtimeService;
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
    private final RealtimeService realtimeService;

    /**
     * Handle achievement earned events asynchronously.
     *
     * @param event the achievement earned event
     */
    @Async
    @EventListener
    public void onAchievementEarned(AchievementEarnedEvent event) {
        try {
            String tierStr = getTierString(event.achievementTier());

            // Q20: use the dispatch service. "badge_earned" is not a known
            // category so the switch falls through to enabled by default;
            // achievements remain visible to all users.
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

            // Also push via WebSocket (per D-05)
            realtimeService.sendNotification(event.userId(),
                BadgeEarnedPayload.of(
                    event.achievementKey(),
                    event.achievementName(),
                    event.achievementDescription(),
                    null, // badgeIcon not available in event
                    getTierString(event.achievementTier()).toLowerCase(),
                    event.userId()
                ));

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
