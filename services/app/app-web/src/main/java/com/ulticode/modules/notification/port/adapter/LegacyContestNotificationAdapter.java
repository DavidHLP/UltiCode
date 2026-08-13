package com.ulticode.modules.notification.port.adapter;

import com.ulticode.app.api.service.ContestNotificationPort;
import com.ulticode.modules.notification.event.NotificationIntentEventPublisher;
import com.ulticode.modules.notification.intent.ContestStartingIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * App-api adapter that records contest-start reminders in the durable
 * notification integration outbox. The Contest lifecycle module remains
 * caller-facing and does not depend on notification channel or ledger types.
 *
 * <p>P7-RELOCATE-CONTEST-001.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyContestNotificationAdapter implements ContestNotificationPort {

    private final NotificationIntentEventPublisher notificationIntentEventPublisher;

    @Override
    public void notifyContestStarting(String userId, String contestId, String contestTitle,
                                      LocalDateTime startTime, String reminderType) {
        ContestStartingIntent intent = ContestStartingIntent.of(
                userId, contestId, contestTitle, startTime, reminderType);
        notificationIntentEventPublisher.publish(intent);
    }
}
