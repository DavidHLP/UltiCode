package com.ulticode.modules.auth.service;

import cn.hutool.core.util.IdUtil;
import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.email.dto.SendEmailDTO;
import com.ulticode.modules.email.service.EmailService;
import com.ulticode.modules.refreshtoken.service.RefreshTokenService;
import com.ulticode.modules.auth.account.AuthAccountPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Password reset service.
 * Handles forgot password and password reset functionality.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final AuthAccountPort accountPort;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;

    private final Clock clock;

    @Value("${app.frontend-url:http://localhost:9002}")
    private String frontendUrl;

    /**
     * Forgot password - send reset email.
     *
     * @param email the user's email address
     */
    @RateLimit(key = "'forgot-password:' + #email", limit = 3, period = 3600)
    public void forgotPassword(String email) {
        User user = accountPort.findByEmail(email).orElse(null);

        if (user == null) {
            // Do not reveal whether user exists (security best practice)
            log.debug("Password reset requested for non-existent email: {}", email);
            return;
        }

        String plainToken = IdUtil.simpleUUID();
        String hashedToken = passwordEncoder.encode(plainToken);
        LocalDateTime expiresAt = LocalDateTime.now(clock).plusMinutes(30);

        accountPort.savePasswordReset(user.getId(), hashedToken,
                expiresAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());

        // D-01: Wire EmailServiceImpl to actually send email
        String resetUrl = frontendUrl + "/reset-password?token=" + plainToken;
        SendEmailDTO emailDto = new SendEmailDTO();
        emailDto.setTo(email);
        emailDto.setSubject("UltiCode - Password Reset");
        emailDto.setHtml("""
            <h2>Password Reset</h2>
            <p>Hello <strong>%s</strong>,</p>
            <p>You requested a password reset. Click the link below to set a new password:</p>
            <p><a href="%s" style="padding: 10px 20px; background-color: #268bd2; color: white; text-decoration: none; border-radius: 4px;">Reset Password</a></p>
            <p>This link expires in 30 minutes. If you did not request this, ignore this email.</p>
            <p>Best regards,<br>UltiCode Team</p>
            """.formatted(user.getUsername(), resetUrl));
        emailDto.setText("Hello %s,\n\nYou requested a password reset. Visit this link to set a new password:\n%s\n\nThis link expires in 30 minutes.\n\nBest regards,\nUltiCode Team".formatted(user.getUsername(), resetUrl));
        emailService.sendEmail(emailDto);

        log.info("Password reset email sent for user: {}", user.getId());
    }

    /**
     * Reset password using token.
     *
     * @param token      the reset token from email
     * @param newPassword the new password
     * @throws BusinessException if token is invalid or expired
     */
    public void resetPassword(String token, String newPassword) {
        List<User> candidates = accountPort.findUsersWithActivePasswordReset(
                java.time.LocalDateTime.now(clock)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant().toEpochMilli());

        User matchedUser = null;
        for (User candidate : candidates) {
            if (passwordEncoder.matches(token, candidate.getPasswordResetTokenHash())) {
                matchedUser = candidate;
                break;
            }
        }

        if (matchedUser == null) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_RESET_TOKEN, "Invalid or expired reset token");
        }

        accountPort.updatePassword(matchedUser.getId(), passwordEncoder.encode(newPassword));
        accountPort.clearPasswordReset(matchedUser.getId());

        // D-17: Revoke all user sessions via Redis after successful password change
        refreshTokenService.revokeAllUserTokens(matchedUser.getId());

        log.info("Password reset completed for user: {}", matchedUser.getId());
    }
}
