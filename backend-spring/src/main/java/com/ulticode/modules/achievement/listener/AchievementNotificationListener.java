package com.ulticode.modules.achievement.listener;

import com.ulticode.modules.achievement.event.AchievementEarnedEvent;
import com.ulticode.modules.notification.dispatcher.NotificationDispatcher;
import com.ulticode.modules.notification.intent.AchievementEarnedIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Async listener for achievement earned events.
 *
 * <p>Listens for {@link AchievementEarnedEvent} and dispatches a typed
 * {@link AchievementEarnedIntent} to the notification delivery module. The
 * dispatcher owns the entire delivery policy: preference gating, per-channel
 * fan-out (InApp row, Email, WebSocket {@code BadgeEarnedPayload}), and
 * ledger-backed idempotency. This listener contributes only the intent — it
 * no longer branches on a rollout flag, builds a legacy envelope, or pushes
 * the WebSocket event inline (the {@code WebSocketNotificationChannel} owns
 * that leg, so there is no double-push).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementNotificationListener {

    private final NotificationDispatcher notificationDispatcher;

    /**
     * Handle achievement earned events asynchronously.
     *
     * @param event the achievement earned event
     */
    @Async
    @EventListener
    public void onAchievementEarned(AchievementEarnedEvent event) {
        try {
            notificationDispatcher.dispatch(AchievementEarnedIntent.of(event));
            log.debug("Created achievement notification for user {}: {}",
                    event.userId(), event.achievementKey());
        } catch (Exception e) {
            log.warn("Failed to create achievement notification for user {}: {}",
                    event.userId(), e.getMessage());
        }
    }
}
