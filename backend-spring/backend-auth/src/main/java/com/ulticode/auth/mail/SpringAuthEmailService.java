package com.ulticode.auth.mail;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Production implementation of {@link AuthEmailService} using Spring JavaMailSender.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpringAuthEmailService implements AuthEmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Override
    public void sendSecurityEmail(String toEmail, String subject, String htmlContent, String textContent) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("JavaMailSender not available; skipping security email dispatch to {}", toEmail);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(textContent, htmlContent);
            mailSender.send(message);
            log.info("Sent security email '{}' to {}", subject, toEmail);
        } catch (Exception e) {
            log.error("Failed to send security email to {}: {}", toEmail, e.getMessage(), e);
        }
    }
}
