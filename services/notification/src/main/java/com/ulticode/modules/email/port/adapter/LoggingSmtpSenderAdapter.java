package com.ulticode.modules.email.port.adapter;

import com.ulticode.modules.email.port.SmtpSenderPort;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Dev / log-only adapter of {@link SmtpSenderPort}. Active when
 * {@code app.email.enabled=false} (the default in dev / test). Replaces
 * the SMTP send with a structured log line so the dispatcher's log-and-status
 * path can be exercised without a running SMTP server.
 *
 * <p>This is the second adapter that justifies the
 * {@link SmtpSenderPort} seam — production tests inject this adapter and
 * assert on the log output; production wiring injects
 * {@code JavaMailSmtpSenderAdapter}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.email.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingSmtpSenderAdapter implements SmtpSenderPort {

    @Override
    public void send(String to, String subject, String html, String text) throws MessagingException {
        log.info("[EMAIL-LOG-ONLY] to={}, subject={}, hasHtml={}, hasText={}",
                to, subject, html != null && !html.isBlank(), text != null && !text.isBlank());
    }
}
