package com.ulticode.modules.notification.channel;

import com.ulticode.modules.notification.intent.NotificationIntent;
import com.ulticode.modules.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * In-app channel — persists a row to the {@code notification} table so the
 * user can see it on the notifications page (ADR-004 §2.2, M4b).
 *
 * <p>Idempotency is owned by the ledger ({@code notification_delivery_ledger});
 * this channel only runs when the dispatcher has successfully claimed the
 * {@code (intentId, "in_app")} slot, so duplicate invocations of the same
 * intent reach at most one {@code insert} here.
 *
 * <p>The WebSocket push is <b>not</b> done here — that is
 * {@link WebSocketNotificationChannel}'s job. Splitting the responsibilities
 * keeps {@code InAppNotificationChannel} the only path that writes the
 * business notification row, and {@code WebSocketNotificationChannel} the
 * only path that pushes the real-time event.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InAppNotificationChannel implements NotificationChannel {

    public static final String CHANNEL_ID = "in_app";

    private final NotificationService notificationService;

    @Override
    public String channelId() {
        return CHANNEL_ID;
    }

    @Override
    public boolean supports(NotificationIntent intent) {
        // InApp is the universal channel: every typed intent is recorded as a
        // row. Per-category preference filtering happens in the dispatcher
        // before this method is called, so we do not re-check it here.
        return true;
    }

    @Override
    public void send(NotificationIntent intent) {
        Map<String, Object> metadata = renderMetadata(intent);
        String title = renderTitle(intent);
        String body = renderBody(intent);
        String type = intent.getClass().getSimpleName();
        String category = intent.category().name();
        String link = renderLink(intent);

        notificationService.createNotificationRowOnly(
                intent.userId(),
                type,
                category,
                title,
                body,
                link,
                metadata);
    }

    // --- Per-intent projection (exhaustive instanceof chain over the sealed type) ---

    private String renderTitle(NotificationIntent intent) {
        if (intent instanceof com.ulticode.modules.notification.intent.SubmissionCompletedIntent s) {
            return "Submission judged: " + s.status().wireValue();
        }
        if (intent instanceof com.ulticode.modules.notification.intent.AchievementEarnedIntent a) {
            return "Achievement Earned: " + a.achievementName();
        }
        if (intent instanceof com.ulticode.modules.notification.intent.ContestStartingIntent c) {
            return "Contest '" + c.contestTitle() + "' starts in " + c.reminderType();
        }
        if (intent instanceof com.ulticode.modules.notification.intent.FollowReceivedIntent f) {
            return f.followerUsername() + " followed you";
        }
        if (intent instanceof com.ulticode.modules.notification.intent.CommentReplyIntent r) {
            return r.replierUsername() + " replied to your comment";
        }
        if (intent instanceof com.ulticode.modules.notification.intent.SystemAlertIntent sy) {
            return sy.title();
        }
        throw new IllegalStateException("Unhandled intent: " + intent.getClass().getName());
    }

    private String renderBody(NotificationIntent intent) {
        if (intent instanceof com.ulticode.modules.notification.intent.SubmissionCompletedIntent s) {
            return s.problemTitle() == null ? "" : s.problemTitle();
        }
        if (intent instanceof com.ulticode.modules.notification.intent.AchievementEarnedIntent a) {
            return (a.achievementDescription() == null ? "" : a.achievementDescription())
                    + " (+" + (a.points() == null ? 0 : a.points()) + " pts)";
        }
        if (intent instanceof com.ulticode.modules.notification.intent.ContestStartingIntent) {
            return "";
        }
        if (intent instanceof com.ulticode.modules.notification.intent.FollowReceivedIntent) {
            return "";
        }
        if (intent instanceof com.ulticode.modules.notification.intent.CommentReplyIntent r) {
            return r.preview() == null ? "" : r.preview();
        }
        if (intent instanceof com.ulticode.modules.notification.intent.SystemAlertIntent sy) {
            return sy.body() == null ? "" : sy.body();
        }
        throw new IllegalStateException("Unhandled intent: " + intent.getClass().getName());
    }

    private String renderLink(NotificationIntent intent) {
        if (intent instanceof com.ulticode.modules.notification.intent.SubmissionCompletedIntent s) {
            return "/submissions/" + s.submissionId();
        }
        if (intent instanceof com.ulticode.modules.notification.intent.AchievementEarnedIntent) {
            return "/achievements";
        }
        if (intent instanceof com.ulticode.modules.notification.intent.ContestStartingIntent c) {
            return "/contest/" + c.contestId();
        }
        if (intent instanceof com.ulticode.modules.notification.intent.FollowReceivedIntent f) {
            return "/profile/" + f.followerUsername();
        }
        if (intent instanceof com.ulticode.modules.notification.intent.CommentReplyIntent r) {
            return r.link() == null ? "" : r.link();
        }
        if (intent instanceof com.ulticode.modules.notification.intent.SystemAlertIntent sy) {
            return sy.link() == null ? "" : sy.link();
        }
        throw new IllegalStateException("Unhandled intent: " + intent.getClass().getName());
    }

    private Map<String, Object> renderMetadata(NotificationIntent intent) {
        Map<String, Object> m = new HashMap<>();
        if (intent instanceof com.ulticode.modules.notification.intent.SubmissionCompletedIntent s) {
            m.put("submissionId", s.submissionId());
            m.put("generation", s.generation());
            m.put("problemId", s.problemId());
            m.put("problemTitle", s.problemTitle() == null ? "" : s.problemTitle());
            m.put("status", s.status().wireValue());
            m.put("isAccepted", s.status() == com.ulticode.modules.submission.enums.SubmissionStatus.ACCEPTED);
            m.put("elapsedMs", s.elapsedMs());
            m.put("memoryBytes", s.memoryBytes());
            if (s.contestId() != null) {
                m.put("contestId", s.contestId());
                m.put("contestScoreDelta", s.contestScoreDelta());
            }
        } else if (intent instanceof com.ulticode.modules.notification.intent.AchievementEarnedIntent a) {
            m.put("achievementId", a.achievementId());
            m.put("achievementKey", a.achievementKey());
            m.put("achievementName", a.achievementName());
            m.put("achievementIcon", a.achievementIconUrl() == null ? "" : a.achievementIconUrl());
            m.put("achievementTier", a.achievementTier());
            m.put("points", a.points());
        } else if (intent instanceof com.ulticode.modules.notification.intent.ContestStartingIntent c) {
            m.put("contestId", c.contestId());
            m.put("contestTitle", c.contestTitle());
            m.put("startTime", c.startTime() == null ? "" : c.startTime().toString());
            m.put("reminderType", c.reminderType());
        } else if (intent instanceof com.ulticode.modules.notification.intent.FollowReceivedIntent f) {
            m.put("followerUserId", f.followerUserId());
            m.put("followerUsername", f.followerUsername());
        } else if (intent instanceof com.ulticode.modules.notification.intent.CommentReplyIntent r) {
            m.put("commentId", r.commentId());
            m.put("replierUserId", r.replierUserId());
            m.put("replierUsername", r.replierUsername());
        } else if (intent instanceof com.ulticode.modules.notification.intent.SystemAlertIntent sy) {
            m.put("alertKey", sy.alertKey());
        } else {
            throw new IllegalStateException("Unhandled intent: " + intent.getClass().getName());
        }
        return m;
    }
}
