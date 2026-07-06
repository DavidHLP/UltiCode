package com.ulticode.modules.email.port;

import jakarta.mail.MessagingException;

/**
 * Outbound SMTP transport port (seam at the email module's external interface).
 *
 * <p>The email module previously coupled the "what to send" logic directly to
 * Spring's {@code JavaMailSender} inside {@code EmailServiceImpl.sendEmail}
 * — the interface was nearly as complex as the implementation, and the
 * transactional state-machine log update was tangled with the SMTP failure
 * path. After the deepening, the SMTP transport lives behind this port so:
 *
 * <ul>
 *   <li>The validator + template resolver + log + dispatch sequence is
 *       testable in isolation by mocking this single method.</li>
 *   <li>The "log only" mode (when {@code app.email.enabled=false}) is a real
 *       adapter rather than a flag-gated branch inside the dispatcher.</li>
 *   <li>A future transport swap (SES, SendGrid, Postmark) means writing a new
 *       adapter; no changes to {@code EmailServiceImpl}.</li>
 * </ul>
 *
 * <p><b>Dependency category:</b> remote but owned (L3 — local-substitutable).
 * The JavaMail adapter is the production adapter; the logging adapter is the
 * dev/test adapter. Two adapters justify the seam.
 *
 * <p>Implementations are responsible for handling their own connection
 * failures: throwing {@link MessagingException} signals a transport failure
 * (the dispatcher maps that to {@code EmailStatus.FAILED}); returning
 * normally signals a successful SMTP conversation.
 */
public interface SmtpSenderPort {

    /**
     * Send the email. Implementations should prefer HTML when {@code html} is
     * non-blank and fall back to plain text when only {@code text} is set.
     *
     * @param to      recipient address (already validated by the dispatcher)
     * @param subject rendered subject line (placeholder substitution done)
     * @param html    rendered HTML body; may be {@code null} or blank
     * @param text    plain-text body; may be {@code null} or blank
     * @throws MessagingException when the SMTP transport rejects the message
     */
    void send(String to, String subject, String html, String text) throws MessagingException;
}