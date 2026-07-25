package com.ulticode.modules.notification.email;

import com.ulticode.modules.email.dto.SendEmailDTO;
import com.ulticode.modules.notification.intent.NotificationIntent;

/**
 * Maps a typed {@link NotificationIntent} to an {@link SendEmailDTO} with
 * the canonical {@code templateId} and variable map.
 *
 * <p>Template IDs are stable identifiers; the actual subject / body are
 * resolved by {@code EmailServiceImpl.sendEmail} from the
 * {@code email_template} table. M4b does <b>not</b> ship the templates —
 * seeding them is a separate ops task (post-M4d) and the email channel
 * treats missing templates as a delivery failure (mark FAILED in the
 * ledger, do not block other channels).
 *
 * <p>Reference: notification/email/EmailTemplates + notification/intent/NotificationIntent;
 * see also V20260613120000__Create_Notification_Delivery_Ledger.sql for the
 * dispatch contract and the per-channel idempotency rule.
 */
public final class EmailTemplates {

    public static final String TEMPLATE_SUBMISSION_COMPLETED = "notification.submission.completed";
    public static final String TEMPLATE_ACHIEVEMENT_EARNED = "notification.achievement.earned";
    public static final String TEMPLATE_CONTEST_STARTING = "notification.contest.starting";
    public static final String TEMPLATE_COMMENT_REPLY = "notification.comment.reply";
    public static final String TEMPLATE_SYSTEM_ALERT = "notification.system.alert";

    private EmailTemplates() {}

    /**
     * Project an intent to a {@link SendEmailDTO} with a {@code templateId}
     * and a variable map. The returned DTO does not include the
     * {@code to} (recipient) field — callers must set it before calling
     * {@code EmailService.sendEmail}.
     *
     * <p>Per ADR-004 §2.5, a missing or null email is the channel's
     * concern; this helper is purely about the template binding.
     */
    public static SendEmailDTO forIntent(NotificationIntent intent) {
        SendEmailDTO dto = new SendEmailDTO();
        if (intent instanceof com.ulticode.modules.notification.intent.SubmissionCompletedIntent s) {
            dto.setTemplateId(TEMPLATE_SUBMISSION_COMPLETED);
            dto.setVariables(java.util.Map.of(
                    "problemTitle", s.problemTitle() == null ? "" : s.problemTitle(),
                    "status", s.status().wireValue(),
                    "isAccepted", s.status() == com.ulticode.modules.submission.enums.SubmissionStatus.ACCEPTED,
                    "elapsedMs", s.elapsedMs(),
                    "memoryBytes", s.memoryBytes(),
                    "contestId", s.contestId() == null ? "" : s.contestId(),
                    "contestScoreDelta", s.contestScoreDelta() == null ? 0L : s.contestScoreDelta()
            ));
        } else if (intent instanceof com.ulticode.modules.notification.intent.AchievementEarnedIntent a) {
            dto.setTemplateId(TEMPLATE_ACHIEVEMENT_EARNED);
            dto.setVariables(java.util.Map.of(
                    "achievementName", a.achievementName() == null ? "" : a.achievementName(),
                    "achievementDescription", a.achievementDescription() == null ? "" : a.achievementDescription(),
                    "achievementIcon", a.achievementIconUrl() == null ? "" : a.achievementIconUrl(),
                    "tier", a.achievementTier() == null ? "Bronze" : tierName(a.achievementTier()),
                    "points", a.points() == null ? 0 : a.points()
            ));
        } else if (intent instanceof com.ulticode.modules.notification.intent.ContestStartingIntent c) {
            dto.setTemplateId(TEMPLATE_CONTEST_STARTING);
            dto.setVariables(java.util.Map.of(
                    "contestTitle", c.contestTitle() == null ? "" : c.contestTitle(),
                    "startTime", c.startTime() == null ? "" : c.startTime().toString(),
                    "reminderType", c.reminderType()
            ));
        } else if (intent instanceof com.ulticode.modules.notification.intent.CommentReplyIntent r) {
            dto.setTemplateId(TEMPLATE_COMMENT_REPLY);
            dto.setVariables(java.util.Map.of(
                    "replierUsername", r.replierUsername() == null ? "" : r.replierUsername(),
                    "preview", r.preview() == null ? "" : r.preview()
            ));
        } else if (intent instanceof com.ulticode.modules.notification.intent.SystemAlertIntent sy) {
            dto.setTemplateId(TEMPLATE_SYSTEM_ALERT);
            dto.setVariables(java.util.Map.of(
                    "title", sy.title() == null ? "" : sy.title(),
                    "body", sy.body() == null ? "" : sy.body()
            ));
        } else if (intent instanceof com.ulticode.modules.notification.intent.FollowReceivedIntent) {
            // FollowReceivedIntent has no email template by design (matrix
            // in the plan: Email channel returns false from supports()).
            // Reaching this branch would be a programmer error in the
            // channel's supports() implementation.
            throw new IllegalStateException(
                    "FollowReceivedIntent has no email template; "
                            + "EmailNotificationChannel.supports() must reject it");
        } else {
            throw new IllegalStateException("Unhandled intent: " + intent.getClass().getName());
        }
        return dto;
    }

    /**
     * Map the legacy tier integer ({@code 1=Bronze, 2=Silver, 3=Gold, 4=Platinum})
     * to the canonical name expected by the email template. Mirrors
     * {@code AchievementNotificationListener.getTierString}.
     */
    public static String tierName(int tier) {
        return switch (tier) {
            case 2 -> "Silver";
            case 3 -> "Gold";
            case 4 -> "Platinum";
            default -> "Bronze";
        };
    }
}
