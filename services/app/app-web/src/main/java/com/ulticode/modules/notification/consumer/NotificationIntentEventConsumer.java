package com.ulticode.modules.notification.consumer;

import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.notification.event.NotificationIntentEventPublisher;
import com.ulticode.modules.notification.dispatcher.NotificationDispatcher;
import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.modules.notification.intent.AchievementEarnedIntent;
import com.ulticode.modules.notification.intent.CommentReplyIntent;
import com.ulticode.modules.notification.intent.ContestStartingIntent;
import com.ulticode.modules.notification.intent.FollowReceivedIntent;
import com.ulticode.modules.notification.intent.NotificationIntent;
import com.ulticode.modules.notification.intent.SubmissionCompletedIntent;
import com.ulticode.modules.notification.intent.SystemAlertIntent;
import com.ulticode.modules.submission.codec.SubmissionStatusCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Converts the durable NotificationIntentCreated envelope back to the typed
 * intent seam and delegates delivery to the retry-aware dispatcher.
 */
@Component
@RequiredArgsConstructor
public class NotificationIntentEventConsumer {

    private final NotificationDispatcher notificationDispatcher;

    /**
     * Consume one inbox payload. Any malformed or identity-inconsistent
     * envelope throws so {@code InboxConsumer} retains it for retry/DEAD.
     */
    public void consume(Map<String, Object> payload) {
        consume(null, payload);
    }

    /**
     * Consume one durable inbox payload and verify its producer event id.
     */
    public void consume(String eventId, Map<String, Object> payload) {
        NotificationIntent intent = toIntent(payload);
        String persistedIntentId = requiredString(payload, "intentId");
        if (!persistedIntentId.equals(intent.intentId())) {
            throw new IllegalArgumentException("Notification intent identity mismatch");
        }
        if (eventId != null
                && !NotificationIntentEventPublisher.eventId(intent.intentId()).equals(eventId)) {
            throw new IllegalArgumentException("Notification event identity mismatch");
        }
        notificationDispatcher.dispatchForDurableRetry(intent);
    }

    static NotificationIntent toIntent(Map<String, Object> payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Notification intent payload must not be null");
        }
        String type = requiredString(payload, "intentType");
        NotificationCategory category = category(payload);
        String userId = requiredString(payload, "userId");

        NotificationIntent intent = switch (type) {
            case "SUBMISSION" -> new SubmissionCompletedIntent(
                    userId,
                    requiredString(payload, "submissionId"),
                    requiredLong(payload, "generation"),
                    SubmissionStatusCodec.fromWire(requiredString(payload, "status")),
                    optionalString(payload.get("problemId")),
                    optionalString(payload.get("problemTitle")),
                    nonNegativeLong(payload.get("elapsedMs")),
                    nonNegativeLong(payload.get("memoryBytes")),
                    optionalString(payload.get("contestId")),
                    optionalLong(payload.get("contestScoreDelta")),
                    category);
            case "ACHIEVEMENT" -> new AchievementEarnedIntent(
                    userId,
                    requiredString(payload, "achievementId"),
                    requiredString(payload, "achievementKey"),
                    requiredString(payload, "achievementName"),
                    optionalString(payload.get("achievementDescription")),
                    optionalString(payload.get("achievementIconUrl")),
                    optionalInteger(payload.get("achievementTier")),
                    optionalInteger(payload.get("points")),
                    optionalInstant(payload.get("earnedAt")),
                    category);
            case "CONTEST_REMINDER" -> new ContestStartingIntent(
                    userId,
                    requiredString(payload, "contestId"),
                    requiredString(payload, "contestTitle"),
                    optionalLocalDateTime(payload.get("startTime")),
                    requiredString(payload, "reminderType"),
                    category);
            case "FOLLOW" -> new FollowReceivedIntent(
                    userId,
                    requiredString(payload, "followerUserId"),
                    requiredString(payload, "followerUsername"),
                    optionalLocalDate(payload.get("followDay")),
                    category);
            case "REPLY" -> new CommentReplyIntent(
                    userId,
                    requiredString(payload, "commentId"),
                    requiredString(payload, "replierUserId"),
                    requiredString(payload, "replierUsername"),
                    optionalString(payload.get("preview")),
                    optionalString(payload.get("link")),
                    category);
            case "SYSTEM" -> new SystemAlertIntent(
                    userId,
                    requiredString(payload, "alertKey"),
                    requiredString(payload, "title"),
                    optionalString(payload.get("body")),
                    optionalString(payload.get("link")),
                    category);
            default -> throw new IllegalArgumentException("Unsupported notification intent type: " + type);
        };
        return intent;
    }

    private static NotificationCategory category(Map<String, Object> payload) {
        try {
            return NotificationCategory.valueOf(requiredString(payload, "category"));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid notification category", e);
        }
    }

    private static String requiredString(Map<String, Object> payload, String key) {
        String value = optionalString(payload.get(key));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing NotificationIntent field: " + key);
        }
        return value;
    }

    private static String optionalString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static long requiredLong(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing NotificationIntent field: " + key);
        }
        long parsed = toLong(value, key);
        if (parsed < 0) {
            throw new IllegalArgumentException("Negative NotificationIntent field: " + key);
        }
        return parsed;
    }

    private static long nonNegativeLong(Object value) {
        if (value == null) {
            return 0L;
        }
        long parsed = toLong(value, "numeric");
        return Math.max(0L, parsed);
    }

    private static long toLong(Object value, String key) {
        try {
            if (value instanceof Byte || value instanceof Short
                    || value instanceof Integer || value instanceof Long) {
                return ((Number) value).longValue();
            }
            return new BigDecimal(String.valueOf(value)).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("Invalid NotificationIntent field: " + key, e);
        }
    }

    private static Long optionalLong(Object value) {
        return value == null ? null : toLong(value, "optional numeric");
    }

    private static Integer optionalInteger(Object value) {
        if (value == null) {
            return null;
        }
        long parsed = toLong(value, "optional integer");
        if (parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("NotificationIntent integer is out of range");
        }
        return (int) parsed;
    }

    private static Instant optionalInstant(Object value) {
        return value == null ? null : Instant.parse(String.valueOf(value));
    }

    private static LocalDate optionalLocalDate(Object value) {
        return value == null ? null : LocalDate.parse(String.valueOf(value));
    }

    private static LocalDateTime optionalLocalDateTime(Object value) {
        return value == null ? null : LocalDateTime.parse(String.valueOf(value));
    }
}
