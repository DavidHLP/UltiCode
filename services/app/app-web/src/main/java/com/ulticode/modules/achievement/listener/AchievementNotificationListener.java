package com.ulticode.modules.achievement.listener;

import com.ulticode.modules.achievement.event.AchievementEarnedEvent;
import com.ulticode.modules.notification.event.NotificationIntentEventPublisher;
import com.ulticode.modules.notification.intent.AchievementEarnedIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Persists achievement notification intents in the App integration outbox.
 *
 * <p><b>BEFORE_COMMIT.</b> The listener writes only the durable outbox row in
 * the same transaction as the {@code UserAchievement} award. It never performs
 * preference lookup, ledger fan-out, SMTP, or WebSocket I/O here. The durable
 * inbox worker owns those delivery concerns after commit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementNotificationListener {

    private final NotificationIntentEventPublisher notificationIntentEventPublisher;

    /**
     * Record the achievement intent before the awarding transaction commits.
     *
     * @param event the achievement earned event
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onAchievementEarned(AchievementEarnedEvent event) {
        notificationIntentEventPublisher.publish(AchievementEarnedIntent.of(event));
        log.debug("Recorded durable achievement notification for user {}: {}",
                event.userId(), event.achievementKey());
    }
}
