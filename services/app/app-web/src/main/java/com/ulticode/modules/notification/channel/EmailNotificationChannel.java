package com.ulticode.modules.notification.channel;

import com.ulticode.modules.email.constants.EmailStatus;
import com.ulticode.modules.email.dto.EmailLogDTO;
import com.ulticode.modules.email.dto.SendEmailDTO;
import com.ulticode.modules.email.service.EmailService;
import com.ulticode.modules.notification.email.EmailTemplates;
import com.ulticode.modules.notification.intent.NotificationIntent;
import com.ulticode.modules.notification.port.UserEmailPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Email channel — looks up the recipient's email and projects the intent to
 * a templated {@code SendEmailDTO} via {@link EmailTemplates}.
 *
 * <p>Behavior on missing email: per ADR-004 §2.5, the channel treats a
 * recipient without an email as an intentional skip. Transport failures are
 * surfaced to the dispatcher so the ledger records {@code FAILED} and can
 * apply its bounded retry policy. We do not block other channels.
 *
 * <p>Reference: notification/channel/EmailNotificationChannel + the per-channel
 * ledger key in V20260613120000__Create_Notification_Delivery_Ledger.sql.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationChannel implements NotificationChannel {

    public static final String CHANNEL_ID = "email";

    private final EmailService emailService;

    /**
     * User lookup is optional: in dev/test environments the user table may
     * not be wired in. We tolerate the bean being absent and treat lookup
     * failure as a missing-recipient (handled in {@link #send}).
     *
     * <p>Package-private to allow unit tests in the same package to inject
     * a mock directly (the {@code @Autowired(required = false)} path is
     * exercised by Spring in production).
     */
    @Autowired(required = false)
    UserEmailPort userEmailPort;

    @Override
    public String channelId() {
        return CHANNEL_ID;
    }

    @Override
    public boolean supports(NotificationIntent intent) {
        if (intent instanceof com.ulticode.modules.notification.intent.SubmissionCompletedIntent s) {
            // Email only for terminal submission outcomes (matches the
            // "isAccepted" emphasis in the original SubmissionServiceImpl
            // dispatch — in-flight results are not useful in an email).
            return s.status().getKind() != com.ulticode.domain.submission.enums.SubmissionStatus.Kind.IN_FLIGHT;
        }
        if (intent instanceof com.ulticode.modules.notification.intent.AchievementEarnedIntent) {
            return true;
        }
        if (intent instanceof com.ulticode.modules.notification.intent.ContestStartingIntent) {
            return true;
        }
        if (intent instanceof com.ulticode.modules.notification.intent.CommentReplyIntent) {
            return true;
        }
        if (intent instanceof com.ulticode.modules.notification.intent.SystemAlertIntent) {
            return true;
        }
        if (intent instanceof com.ulticode.modules.notification.intent.FollowReceivedIntent) {
            return false;
        }
        throw new IllegalStateException("Unhandled intent: " + intent.getClass().getName());
    }

    @Override
    public void send(NotificationIntent intent) {
        String recipient = resolveRecipientEmail(intent.userId());
        if (recipient == null) {
            // ADR-004 §2.5: email failures are best-effort. A user without
            // an email on file is not a delivery failure — it is a "this
            // channel cannot reach this user" condition. We log at debug
            // and return normally so the dispatcher marks the ledger row
            // DELIVERED (we tried, no error) instead of FAILED (we tried
            // and failed). Throwing here used to cause warn-spam per
            // dispatch for every user that hasn't filled in an email.
            log.debug("Skipping email channel for intent {}: no email on user {}",
                    intent.intentId(), intent.userId());
            return;
        }
        SendEmailDTO dto = EmailTemplates.forIntent(intent);
        dto.setTo(recipient);
        EmailLogDTO result = emailService.sendEmail(dto);
        if (result != null && EmailStatus.FAILED.equals(result.getStatus())) {
            throw new IllegalStateException("Email transport failed for intent "
                    + intent.intentId());
        }
    }

    private String resolveRecipientEmail(String userId) {
        if (userEmailPort == null) {
            return null;
        }
        String email = userEmailPort.findEmail(userId);
        if (email == null || email.isBlank()) {
            return null;
        }
        return email;
    }
}
