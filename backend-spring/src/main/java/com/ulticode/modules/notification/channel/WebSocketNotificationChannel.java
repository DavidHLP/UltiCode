package com.ulticode.modules.notification.channel;

import com.ulticode.modules.achievement.port.BadgePushPort;
import com.ulticode.modules.notification.intent.NotificationIntent;
import com.ulticode.modules.notification.port.NotificationPushPort;
import com.ulticode.modules.websocket.notification.dto.BadgeEarnedPayload;
import com.ulticode.modules.websocket.notification.dto.NotificationPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * WebSocket channel — pushes a real-time event to the recipient's
 * {@code /user/queue/notifications} STOMP queue.
 *
 * <p>Per ADR-004 §2.5 the WebSocket is best-effort: if the user is offline
 * the push is lost (this is the current contract across the codebase). The
 * dispatcher's ledger row is marked {@code DELIVERED} regardless because
 * the call to {@link NotificationPushPort#pushToUser} returns void and
 * does not throw on a missing session.
 *
 * <p>Post-Candidate-4: this channel now depends on the consumer-owned
 * {@link NotificationPushPort} (defined in the notification module) rather
 * than reaching across into {@code com.ulticode.modules.websocket.*}. The
 * dependency direction is fully inverted; the notification module is
 * self-contained except for the wire-format DTOs which are the shared
 * language.
 *
 * <p>Two payload shapes are used:
 * <ul>
 *   <li>{@link NotificationPayload} — generic envelope (used by 5 of 6
 *       intents). The {@code event} field is set per intent so the client
 *       can dispatch on it.</li>
 *   <li>{@link BadgeEarnedPayload} — the existing typed achievement event
 *       consumed by the frontend; reused as-is for backward compatibility
 *       with {@code BadgeEarnedPayload.of(...)} consumers in
 *       {@code management/} and {@code console/}.</li>
 * </ul>
 *
 * <p>Reference: docs/adr/ADR-004-notification-intents.md §2.2 (WebSocketNotificationChannel).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketNotificationChannel implements NotificationChannel {

    public static final String CHANNEL_ID = "websocket";

    private final NotificationPushPort notificationPushPort;
    private final BadgePushPort badgePushPort;

    @Override
    public String channelId() {
        return CHANNEL_ID;
    }

    @Override
    public boolean supports(NotificationIntent intent) {
        // All 6 intents have a meaningful WS event. Per-channel routing
        // (e.g. should contest reminders push?) is a product decision that
        // can be added later by gating here.
        return true;
    }

    @Override
    public void send(NotificationIntent intent) {
        if (intent instanceof com.ulticode.modules.notification.intent.AchievementEarnedIntent a) {
            // Achievement intents push the typed BadgeEarnedPayload via the
            // achievement module's BadgePushPort. The notification module
            // no longer reaches across to decide which typed DTO is right
            // for which domain — the consumer of the domain owns the seam.
            badgePushPort.pushBadgeEarned(intent.userId(),
                    BadgeEarnedPayload.of(
                            a.achievementKey(),
                            a.achievementName(),
                            a.achievementDescription(),
                            a.achievementIconUrl(),
                            tierSlug(a.achievementTier()),
                            a.userId()));
        } else if (intent instanceof com.ulticode.modules.notification.intent.SubmissionCompletedIntent s) {
            notificationPushPort.pushToUser(intent.userId(),
                    NotificationPayload.of(
                            s.intentId(),
                            "SUBMISSION",
                            "Submission judged: " + s.status().wireValue(),
                            s.problemTitle() == null ? "" : s.problemTitle(),
                            java.util.Map.of(
                                    "submissionId", s.submissionId(),
                                    "problemId", s.problemId() == null ? "" : s.problemId(),
                                    "status", s.status().wireValue(),
                                    "isAccepted", s.status()
                                            == com.ulticode.modules.submission.enums.SubmissionStatus.ACCEPTED,
                                    "elapsedMs", s.elapsedMs(),
                                    "memoryBytes", s.memoryBytes())));
        } else if (intent instanceof com.ulticode.modules.notification.intent.ContestStartingIntent c) {
            notificationPushPort.pushToUser(intent.userId(),
                    NotificationPayload.of(
                            c.intentId(),
                            "CONTEST_REMINDER",
                            "Contest '" + c.contestTitle() + "' starts in " + c.reminderType(),
                            "",
                            java.util.Map.of(
                                    "contestId", c.contestId(),
                                    "reminderType", c.reminderType(),
                                    "startTime", c.startTime() == null ? "" : c.startTime().toString())));
        } else if (intent instanceof com.ulticode.modules.notification.intent.FollowReceivedIntent f) {
            notificationPushPort.pushToUser(intent.userId(),
                    NotificationPayload.of(
                            f.intentId(),
                            "FOLLOW",
                            f.followerUsername() + " followed you",
                            "",
                            java.util.Map.of(
                                    "followerUserId", f.followerUserId(),
                                    "followerUsername", f.followerUsername())));
        } else if (intent instanceof com.ulticode.modules.notification.intent.CommentReplyIntent r) {
            notificationPushPort.pushToUser(intent.userId(),
                    NotificationPayload.of(
                            r.intentId(),
                            "REPLY",
                            r.replierUsername() + " replied to your comment",
                            r.preview() == null ? "" : r.preview(),
                            java.util.Map.of(
                                    "commentId", r.commentId(),
                                    "replierUserId", r.replierUserId())));
        } else if (intent instanceof com.ulticode.modules.notification.intent.SystemAlertIntent sy) {
            notificationPushPort.pushToUser(intent.userId(),
                    NotificationPayload.of(
                            sy.intentId(),
                            "SYSTEM",
                            sy.title(),
                            sy.body() == null ? "" : sy.body(),
                            java.util.Map.of("alertKey", sy.alertKey())));
        } else {
            throw new IllegalStateException("Unhandled intent: " + intent.getClass().getName());
        }
    }

    /**
     * Map the legacy tier integer to the slug used by the frontend
     * (matches {@code BadgeEarnedPayload.BadgeTier} constants).
     */
    private static String tierSlug(Integer tier) {
        if (tier == null) {
            return "bronze";
        }
        return switch (tier) {
            case 2 -> "silver";
            case 3 -> "gold";
            case 4 -> "platinum";
            default -> "bronze";
        };
    }
}
