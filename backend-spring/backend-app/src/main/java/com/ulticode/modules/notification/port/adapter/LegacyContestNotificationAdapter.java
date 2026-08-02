package com.ulticode.modules.notification.port.adapter;

import com.ulticode.app.api.service.ContestNotificationPort;
import com.ulticode.modules.notification.dispatcher.NotificationDispatcher;
import com.ulticode.modules.notification.intent.ContestStartingIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Legacy adapter implementing the app-api {@link ContestNotificationPort}.
 *
 * <p>Delegates to the legacy {@link NotificationDispatcher} via the
 * native-params {@link ContestStartingIntent#of(String, String, String, LocalDateTime, String)}
 * factory so that backend-app's {@code ContestLifecycleServiceImpl} can
 * dispatch contest-start reminders without importing the notification module.
 *
 * <p>P7-RELOCATE-CONTEST-001.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyContestNotificationAdapter implements ContestNotificationPort {

    private final NotificationDispatcher notificationDispatcher;

    @Override
    public void notifyContestStarting(String userId, String contestId, String contestTitle,
                                      LocalDateTime startTime, String reminderType) {
        ContestStartingIntent intent = ContestStartingIntent.of(
                userId, contestId, contestTitle, startTime, reminderType);
        notificationDispatcher.dispatch(intent);
    }
}
