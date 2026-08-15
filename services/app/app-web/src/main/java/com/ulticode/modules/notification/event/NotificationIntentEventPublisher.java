package com.ulticode.modules.notification.event;

import com.ulticode.app.api.event.NotificationIntentEventContract;
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

    public static final String EVENT_TYPE = NotificationIntentEventContract.EVENT_TYPE;
    private static final String OWNER = NotificationIntentEventContract.OWNER;
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
        payload.put(NotificationIntentEventContract.INTENT_TYPE, intent.wireType());
        payload.put(NotificationIntentEventContract.INTENT_ID, intent.intentId());
        payload.put(NotificationIntentEventContract.USER_ID, intent.userId());
        payload.put(NotificationIntentEventContract.CATEGORY, intent.category().name());

        if (intent instanceof SubmissionCompletedIntent submission) {
            payload.put(NotificationIntentEventContract.SUBMISSION_ID, submission.submissionId());
            payload.put(NotificationIntentEventContract.GENERATION, submission.generation());
            payload.put(NotificationIntentEventContract.STATUS, submission.status().wireValue());
            payload.put(NotificationIntentEventContract.PROBLEM_ID, submission.problemId());
            payload.put(NotificationIntentEventContract.PROBLEM_TITLE, submission.problemTitle());
            payload.put(NotificationIntentEventContract.ELAPSED_MS, submission.elapsedMs());
            payload.put(NotificationIntentEventContract.MEMORY_BYTES, submission.memoryBytes());
            payload.put(NotificationIntentEventContract.CONTEST_ID, submission.contestId());
            payload.put(NotificationIntentEventContract.CONTEST_SCORE_DELTA,
                    submission.contestScoreDelta());
        } else if (intent instanceof AchievementEarnedIntent achievement) {
            payload.put(NotificationIntentEventContract.ACHIEVEMENT_ID, achievement.achievementId());
            payload.put(NotificationIntentEventContract.ACHIEVEMENT_KEY, achievement.achievementKey());
            payload.put(NotificationIntentEventContract.ACHIEVEMENT_NAME, achievement.achievementName());
            payload.put(NotificationIntentEventContract.ACHIEVEMENT_DESCRIPTION,
                    achievement.achievementDescription());
            payload.put(NotificationIntentEventContract.ACHIEVEMENT_ICON_URL,
                    achievement.achievementIconUrl());
            payload.put(NotificationIntentEventContract.ACHIEVEMENT_TIER, achievement.achievementTier());
            payload.put(NotificationIntentEventContract.POINTS, achievement.points());
            payload.put(NotificationIntentEventContract.EARNED_AT,
                    achievement.earnedAt() == null ? null : achievement.earnedAt().toString());
        } else if (intent instanceof ContestStartingIntent contest) {
            payload.put(NotificationIntentEventContract.CONTEST_ID, contest.contestId());
            payload.put(NotificationIntentEventContract.CONTEST_TITLE, contest.contestTitle());
            payload.put(NotificationIntentEventContract.START_TIME,
                    contest.startTime() == null ? null : contest.startTime().toString());
            payload.put(NotificationIntentEventContract.REMINDER_TYPE, contest.reminderType());
        } else if (intent instanceof FollowReceivedIntent follow) {
            payload.put(NotificationIntentEventContract.FOLLOWER_USER_ID, follow.followerUserId());
            payload.put(NotificationIntentEventContract.FOLLOWER_USERNAME, follow.followerUsername());
            payload.put(NotificationIntentEventContract.FOLLOW_DAY,
                    follow.followDay() == null ? null : follow.followDay().toString());
        } else if (intent instanceof CommentReplyIntent reply) {
            payload.put(NotificationIntentEventContract.COMMENT_ID, reply.commentId());
            payload.put(NotificationIntentEventContract.REPLIER_USER_ID, reply.replierUserId());
            payload.put(NotificationIntentEventContract.REPLIER_USERNAME, reply.replierUsername());
            payload.put(NotificationIntentEventContract.PREVIEW, reply.preview());
            payload.put(NotificationIntentEventContract.LINK, reply.link());
        } else if (intent instanceof SystemAlertIntent alert) {
            payload.put(NotificationIntentEventContract.ALERT_KEY, alert.alertKey());
            payload.put(NotificationIntentEventContract.TITLE, alert.title());
            payload.put(NotificationIntentEventContract.BODY, alert.body());
            payload.put(NotificationIntentEventContract.LINK, alert.link());
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
