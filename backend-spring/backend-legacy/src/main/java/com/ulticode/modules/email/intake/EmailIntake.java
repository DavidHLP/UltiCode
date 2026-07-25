package com.ulticode.modules.email.intake;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.email.constants.EmailStatus;
import com.ulticode.modules.email.dto.EmailLogDTO;
import com.ulticode.modules.email.dto.SendEmailDTO;
import com.ulticode.modules.email.entity.EmailLog;
import com.ulticode.modules.email.entity.EmailTemplate;
import com.ulticode.modules.email.mapper.EmailLogMapper;
import com.ulticode.modules.email.mapper.EmailTemplateMapper;
import com.ulticode.modules.email.port.EmailRenderPort;
import com.ulticode.modules.email.port.SmtpSenderPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.regex.Pattern;

/**
 * Deep module owning the email send pipeline. Replaces the inline 56-line
 * {@code sendEmail} method that used to live in {@code EmailServiceImpl}.
 *
 * <p>Five collaborators:
 * <ol>
 *   <li>{@link EmailTemplateMapper} — load the template row (when
 *       {@code templateId} is set).</li>
 *   <li>{@link EmailRenderPort} — substitute variables into subject / body.</li>
 *   <li>{@link EmailLogMapper} — persist the durable send log (the email
 *       module's "ledger"; per ADR-0004 mirror terminology).</li>
 *   <li>{@link SmtpSenderPort} — outbound transport (JavaMail in prod,
 *       log-only in dev/test).</li>
 *   <li>(internal) — recipient address validation.</li>
 * </ol>
 *
 * <p>Same atomicity contract as the legacy inline method:
 * {@code PENDING} → {@code SENT}/{@code FAILED} log row lives or dies with the
 * transport call's outcome. SMTP exceptions are caught and translated to a
 * {@code FAILED} log row so the caller's {@code Result} always carries a
 * log entry — the caller can poll the log to distinguish "sent" from
 * "transport rejected".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailIntake {

    private final Clock clock;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    private final EmailTemplateMapper templateMapper;
    private final EmailLogMapper logMapper;
    private final EmailRenderPort emailRenderPort;
    private final SmtpSenderPort smtpSenderPort;

    /**
     * Run the full send pipeline and return the durable log entry. The
     * controller's {@code Result&lt;EmailLogDTO&gt;} wraps the log; the caller
     * can inspect {@code status} to see what actually happened.
     */
    @Transactional
    public EmailLogDTO send(SendEmailDTO dto) {
        validateRecipient(dto.getTo());

        String subject = dto.getSubject();
        String html = dto.getHtml();
        String text = dto.getText();
        String templateId = dto.getTemplateId();
        var variables = dto.getVariables() != null ? dto.getVariables() : Collections.<String, Object>emptyMap();

        if (StringUtils.hasText(templateId)) {
            EmailTemplate template = templateMapper.selectById(templateId);
            if (template == null) {
                throw new BusinessException(ErrorCode.EMAIL_TEMPLATE_NOT_FOUND);
            }
            subject = emailRenderPort.render(template.getSubject(), variables);
            html = emailRenderPort.render(template.getBody(), variables);
        }

        EmailLog emailLog = persistPendingLog(dto.getTo(), templateId, subject);
        try {
            smtpSenderPort.send(dto.getTo(), subject, html, text);
            markSent(emailLog);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", dto.getTo(), e.getMessage(), e);
            markFailed(emailLog);
        }
        return toLogDTO(emailLog);
    }

    private void validateRecipient(String to) {
        if (!isValidEmail(to)) {
            throw new BusinessException(ErrorCode.EMAIL_INVALID_RECIPIENT);
        }
    }

    private boolean isValidEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }

    private EmailLog persistPendingLog(String recipient, String templateId, String subject) {
        EmailLog emailLog = new EmailLog();
        emailLog.setTemplateId(templateId);
        emailLog.setRecipient(recipient);
        emailLog.setSubject(subject);
        emailLog.setStatus(EmailStatus.PENDING);
        logMapper.insert(emailLog);
        return emailLog;
    }

    private void markSent(EmailLog emailLog) {
        emailLog.setStatus(EmailStatus.SENT);
        emailLog.setSentAt(LocalDateTime.now(clock));
        logMapper.updateById(emailLog);
    }

    private void markFailed(EmailLog emailLog) {
        emailLog.setStatus(EmailStatus.FAILED);
        emailLog.setError("Failed to send email");
        logMapper.updateById(emailLog);
    }

    private EmailLogDTO toLogDTO(EmailLog log) {
        EmailLogDTO dto = new EmailLogDTO();
        dto.setId(log.getId());
        dto.setTemplateId(log.getTemplateId());
        dto.setRecipient(log.getRecipient());
        dto.setSubject(log.getSubject());
        dto.setStatus(log.getStatus());
        dto.setSentAt(log.getSentAt());
        dto.setError(log.getError());
        dto.setCreatedAt(log.getCreatedAt());
        return dto;
    }
}