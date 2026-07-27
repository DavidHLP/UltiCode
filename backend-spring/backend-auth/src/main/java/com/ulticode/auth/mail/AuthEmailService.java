package com.ulticode.auth.mail;

/**
 * Security email dispatch service owned by backend-auth.
 */
public interface AuthEmailService {

    /**
     * Send a security email (e.g. password reset link).
     */
    void sendSecurityEmail(String toEmail, String subject, String htmlContent, String textContent);
}
