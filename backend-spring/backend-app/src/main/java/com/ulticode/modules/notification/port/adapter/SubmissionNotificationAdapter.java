package com.ulticode.modules.notification.port.adapter;

import com.ulticode.app.api.service.SubmissionNotificationPort;
import com.ulticode.modules.notification.dispatcher.NotificationDispatcher;
import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.modules.notification.intent.SubmissionCompletedIntent;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Production adapter for {@link SubmissionNotificationPort}, wiring the
 * submission module's fire-and-forget seam into the notification deep module.
 *
 * <p>Delegates to {@link NotificationDispatcher#dispatch} via a
 * {@link SubmissionCompletedIntent}. Per ADR-004 §2.5, failures are logged
 * and never propagated.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubmissionNotificationAdapter implements SubmissionNotificationPort {

    private final NotificationDispatcher notificationDispatcher;

    @Override
    public void dispatchSubmissionCompleted(String submissionId, String userId,
                                            Long problemId, boolean accepted, String verdict) {
        try {
            SubmissionStatus status = accepted
                    ? SubmissionStatus.ACCEPTED
                    : parseStatus(verdict);
            SubmissionCompletedIntent intent = new SubmissionCompletedIntent(
                    userId, submissionId, 0L, status,
                    problemId != null ? problemId.toString() : null,
                    null, 0L, 0L, null, null,
                    NotificationCategory.SYSTEM);
            notificationDispatcher.dispatch(intent);
        } catch (Exception e) {
            log.warn("Failed to dispatch submission notification for submission {}: {}",
                    submissionId, e.getMessage());
        }
    }

    private static SubmissionStatus parseStatus(String verdict) {
        try {
            return SubmissionStatus.valueOf(verdict);
        } catch (Exception e) {
            return SubmissionStatus.WRONG_ANSWER;
        }
    }
}
