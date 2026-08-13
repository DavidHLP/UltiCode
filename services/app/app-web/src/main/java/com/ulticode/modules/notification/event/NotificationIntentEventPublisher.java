package com.ulticode.modules.notification.event;

import com.ulticode.modules.event.outbox.IntegrationEventPublisher;
import com.ulticode.modules.notification.intent.AchievementEarnedIntent;
import com.ulticode.modules.notification.intent.CommentReplyIntent;
import com.ulticode.modules.notification.intent.ContestStartingIntent;
import com.ulticode.modules.notification.intent.FollowReceivedIntent;
import com.ulticode.modules.notification.intent.NotificationIntent;
import com.ulticode.modules.notification.intent.SubmissionCompletedIntent;
import com.ulticode.modules.notification.intent.SystemAlertIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes typed notification intents through App's durable integration outbox.
 *
 * <p>The outbox event id is a deterministic, bounded representation of the
 * intent id. Repeating the same source event therefore hits the existing
 * {@code insertIfAbsent} fence instead of creating another transport event.
 * The intent payload contains only channel projection data; credentials,
 * tokens, cookies and hidden-test data are not part of this envelope.
 */
@Component
@RequiredArgsConstructor
public class NotificationIntentEventPublisher {

    public static final String EVENT_TYPE = "NotificationIntentCreated";
    private static final String OWNER = "App";
    private static final int EVENT_ID_LENGTH = 40;

    private final IntegrationEventPublisher integrationEventPublisher;

    /**
     * Record one notification intent after the caller's business transaction
     * has established the source fact, or in its current transaction when one
     * is already active.
     *
     * @return the deterministic integration event id
     */
    @Transactional
    public String publish(NotificationIntent intent) {
        if (intent == null) {
            throw new IllegalArgumentException("Notification intent must not be null");
        }
        if (intent.userId() == null || intent.userId().isBlank()
                || intent.category() == null
                || intent.wireType() == null || intent.wireType().isBlank()) {
            throw new IllegalArgumentException("Notification intent routing fields are invalid");
        }

        String eventId = eventId(intent.intentId());
        String aggregateId = intent.intentId();
        long aggregateVersion = 0L;
        if (intent instanceof SubmissionCompletedIntent submission) {
            aggregateId = submission.submissionId();
            aggregateVersion = submission.generation();
        }
        integrationEventPublisher.publishWithId(
                eventId,
                OWNER,
                EVENT_TYPE,
                aggregateId,
                aggregateVersion,
                null,
                null,
                payload(intent));
        return eventId;
    }

    private static Map<String, Object> payload(NotificationIntent intent) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("intentType", intent.wireType());
        payload.put("intentId", intent.intentId());
        payload.put("userId", intent.userId());
        payload.put("category", intent.category().name());

        if (intent instanceof SubmissionCompletedIntent submission) {
            payload.put("submissionId", submission.submissionId());
            payload.put("generation", submission.generation());
            payload.put("status", submission.status().wireValue());
            payload.put("problemId", submission.problemId());
            payload.put("problemTitle", submission.problemTitle());
            payload.put("elapsedMs", submission.elapsedMs());
            payload.put("memoryBytes", submission.memoryBytes());
            payload.put("contestId", submission.contestId());
            payload.put("contestScoreDelta", submission.contestScoreDelta());
        } else if (intent instanceof AchievementEarnedIntent achievement) {
            payload.put("achievementId", achievement.achievementId());
            payload.put("achievementKey", achievement.achievementKey());
            payload.put("achievementName", achievement.achievementName());
            payload.put("achievementDescription", achievement.achievementDescription());
            payload.put("achievementIconUrl", achievement.achievementIconUrl());
            payload.put("achievementTier", achievement.achievementTier());
            payload.put("points", achievement.points());
            payload.put("earnedAt", achievement.earnedAt() == null ? null : achievement.earnedAt().toString());
        } else if (intent instanceof ContestStartingIntent contest) {
            payload.put("contestId", contest.contestId());
            payload.put("contestTitle", contest.contestTitle());
            payload.put("startTime", contest.startTime() == null ? null : contest.startTime().toString());
            payload.put("reminderType", contest.reminderType());
        } else if (intent instanceof FollowReceivedIntent follow) {
            payload.put("followerUserId", follow.followerUserId());
            payload.put("followerUsername", follow.followerUsername());
            payload.put("followDay", follow.followDay() == null ? null : follow.followDay().toString());
        } else if (intent instanceof CommentReplyIntent reply) {
            payload.put("commentId", reply.commentId());
            payload.put("replierUserId", reply.replierUserId());
            payload.put("replierUsername", reply.replierUsername());
            payload.put("preview", reply.preview());
            payload.put("link", reply.link());
        } else if (intent instanceof SystemAlertIntent alert) {
            payload.put("alertKey", alert.alertKey());
            payload.put("title", alert.title());
            payload.put("body", alert.body());
            payload.put("link", alert.link());
        } else {
            throw new IllegalArgumentException("Unsupported notification intent: "
                    + intent.getClass().getName());
        }
        return payload;
    }

    public static String eventId(String intentId) {
        if (intentId == null || intentId.isBlank()) {
            throw new IllegalArgumentException("Notification intent id must not be blank");
        }
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256")
                    .digest(intentId.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }

        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hex.append(String.format("%02x", value));
        }
        return "notification-" + hex.substring(0, EVENT_ID_LENGTH - "notification-".length());
    }
}
