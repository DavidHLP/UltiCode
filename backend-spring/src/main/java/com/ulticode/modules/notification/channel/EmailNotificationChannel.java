package com.ulticode.modules.notification.channel;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.email.dto.SendEmailDTO;
import com.ulticode.modules.email.service.EmailService;
import com.ulticode.modules.notification.email.EmailTemplates;
import com.ulticode.modules.notification.intent.NotificationIntent;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Email channel — looks up the recipient's email and projects the intent to
 * a templated {@code SendEmailDTO} via {@link EmailTemplates}.
 *
 * <p>Behavior on missing email or missing template: per ADR-004 §2.5, email
 * failures are best-effort — we catch {@link BusinessException} from
 * {@code EmailService.sendEmail} and let the dispatcher mark the ledger row
 * {@code FAILED}. We do not block other channels.
 *
 * <p>Reference: docs/adr/ADR-004-notification-intents.md §2.2 (EmailNotificationChannel).
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
    UserMapper userMapper;

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
            return s.status().getKind() != com.ulticode.modules.submission.enums.SubmissionStatus.Kind.IN_FLIGHT;
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
            // Throwing causes dispatcher to mark FAILED with a clear reason.
            throw new BusinessException(com.ulticode.common.exception.ErrorCode.EMAIL_INVALID_RECIPIENT,
                    "No email on file for user " + intent.userId());
        }
        SendEmailDTO dto = EmailTemplates.forIntent(intent);
        dto.setTo(recipient);
        emailService.sendEmail(dto);
    }

    private String resolveRecipientEmail(String userId) {
        if (userMapper == null) {
            return null;
        }
        User u = userMapper.selectById(userId);
        if (u == null || u.getEmail() == null || u.getEmail().isBlank()) {
            return null;
        }
        return u.getEmail();
    }
}
