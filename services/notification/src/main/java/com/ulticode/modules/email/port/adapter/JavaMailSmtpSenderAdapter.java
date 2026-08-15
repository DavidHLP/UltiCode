package com.ulticode.modules.email.port.adapter;

import com.ulticode.modules.email.port.SmtpSenderPort;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Production adapter of {@link SmtpSenderPort} — talks SMTP via Spring's
 * {@link JavaMailSender}. Active when {@code app.email.enabled=true}; the
 * {@code LoggingSmtpSenderAdapter} is the alternative when the flag is off.
 *
 * <p>Format selection (HTML vs. plain text) and the from-address / display
 * name formatting are transport concerns that belong here, not in the
 * dispatcher. The dispatcher hands the adapter pre-validated, pre-rendered
 * strings; the adapter decides how to encode them.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.email.enabled", havingValue = "true")
public class JavaMailSmtpSenderAdapter implements SmtpSenderPort {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@example.com}")
    private String fromEmail;

    @Value("${app.email.from-name:UltiCode}")
    private String fromName;

    @Override
    public void send(String to, String subject, String html, String text) throws MessagingException {
        if (StringUtils.hasText(html)) {
            // HTML email
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(String.format("%s <%s>", fromName, fromEmail));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
            mailSender.send(message);
        } else if (StringUtils.hasText(text)) {
            // Plain-text email
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
        } else {
            log.warn("Skipping SMTP send for recipient {}: no body content", to);
        }
    }
}
