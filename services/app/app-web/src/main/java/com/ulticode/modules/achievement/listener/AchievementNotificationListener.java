package com.ulticode.modules.achievement.listener;

import com.ulticode.modules.achievement.event.AchievementEarnedEvent;
import com.ulticode.modules.notification.dispatcher.NotificationDispatcher;
import com.ulticode.modules.notification.intent.AchievementEarnedIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Async listener for achievement earned events.
 *
 * <p>Listens for {@link AchievementEarnedEvent} and dispatches a typed
 * {@link AchievementEarnedIntent} to the notification delivery module. The
 * dispatcher owns the entire delivery policy: preference gating, per-channel
 * fan-out (InApp row, Email, WebSocket {@code BadgeEarnedPayload}), and
 * ledger-backed idempotency. This listener contributes only the intent.
 *
 * <p><b>AFTER_COMMIT.</b> The listener fires only after the awarding
 * transaction commits, so a WebSocket/InApp delivery failure can never roll
 * back the persisted {@code UserAchievement} row. Combined with the producer
 * (which publishes the event but no longer pushes the badge inline), the
 * WebSocket {@code BadgeEarnedPayload} is pushed exactly once — via this
 * listener → dispatcher → {@code WebSocketNotificationChannel} — with no
 * double-push. {@code fallbackExecution=true} keeps the path active for
 * callers that award outside a transaction.
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
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
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
