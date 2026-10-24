package com.ulticode.modules.notification.consumer;

import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.notification.dispatcher.NotificationDispatcher;
import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.modules.notification.intent.SubmissionCompletedIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.ulticode.submission.api.event.SubmissionLifecycleEventContract.requiredJudgedLong;

/**
 * Handles the durable {@code SubmissionJudged} event for Notification.
 *
 * <p>The inbox owns event identity and retry state. This handler only translates
 * the stable event payload into the existing notification intent seam, preserving
 * the channel fan-out and ledger semantics behind {@link NotificationDispatcher}.
 */
@Component
@RequiredArgsConstructor
public class SubmissionJudgedNotificationConsumer {

    private final NotificationDispatcher notificationDispatcher;

    /**
     * Dispatch one persisted {@code SubmissionJudged} payload.
     *
     * <p>Malformed identity or verdict data is rejected so the inbox can retry
     * or move the row to its dead-letter state instead of acknowledging it.
     */
    public void consume(Map<String, Object> payload) {
        String submissionId = requiredString(payload, "submissionId");
        String userId = requiredString(payload, "userId");
        SubmissionStatus status = SubmissionStatus.fromWire(
                requiredString(payload, "verdict"));

        if (!status.isTerminal()) {
            return;
        }

        long generation = requiredJudgedLong(payload, "generation");
        long elapsedMs = nonNegativeLong(payload.get("runtimeMs"));
        long memoryBytes = memoryBytes(payload.get("memoryMb"));
        String problemId = optionalString(payload.get("problemId"));

        SubmissionCompletedIntent intent = new SubmissionCompletedIntent(
                userId,
                submissionId,
                generation,
                status,
                problemId,
                null,
                elapsedMs,
                memoryBytes,
                null,
                null,
                NotificationCategory.SYSTEM);
        notificationDispatcher.dispatchForDurableRetry(intent);
    }

    private static String requiredString(Map<String, Object> payload, String key) {
        String value = optionalString(payload.get(key));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing SubmissionJudged field: " + key);
        }
        return value;
    }

    private static String optionalString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static long nonNegativeLong(Object value) {
        if (value instanceof Number number) {
            return Math.max(0L, number.longValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            return Math.max(0L, Long.parseLong(text));
        }
        return 0L;
    }

    private static long memoryBytes(Object value) {
        if (value instanceof Number number) {
            return Math.max(0L, (long) (number.doubleValue() * 1024L * 1024L));
        }
        if (value instanceof String text && !text.isBlank()) {
            return Math.max(0L, (long) (Double.parseDouble(text) * 1024L * 1024L));
        }
        return 0L;
    }
}
