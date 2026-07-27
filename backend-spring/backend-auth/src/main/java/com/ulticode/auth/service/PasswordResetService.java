package com.ulticode.auth.service;

import cn.hutool.core.util.IdUtil;
import com.ulticode.auth.account.AuthAccountPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.error.AuthErrorCode;
import com.ulticode.auth.mail.AuthEmailService;
import com.ulticode.auth.refreshtoken.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Password reset service inside backend-auth.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final AuthAccountPort accountPort;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuthEmailService emailService;
    private final Clock clock;

    @Value("${app.frontend-url:http://localhost:9002}")
    private String frontendUrl;

    public void forgotPassword(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        AuthAccountRecord account = accountPort.findByEmail(email.trim()).orElse(null);

        if (account == null) {
            log.debug("Password reset requested for non-existent email: {}", email);
            return;
        }

        String plainToken = IdUtil.simpleUUID();
        String hashedToken = passwordEncoder.encode(plainToken);
        LocalDateTime expiresAt = LocalDateTime.now(clock).plusMinutes(30);

        accountPort.savePasswordReset(
                account.id(),
                hashedToken,
                expiresAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        );

        String resetUrl = frontendUrl + "/reset-password?token=" + plainToken;
        String htmlBody = """
            <h2>Password Reset</h2>
            <p>Hello <strong>%s</strong>,</p>
            <p>You requested a password reset. Click the link below to set a new password:</p>
            <p><a href="%s" style="padding: 10px 20px; background-color: #268bd2; color: white; text-decoration: none; border-radius: 4px;">Reset Password</a></p>
            <p>This link expires in 30 minutes. If you did not request this, ignore this email.</p>
            <p>Best regards,<br>UltiCode Team</p>
            """.formatted(account.username(), resetUrl);
        String textBody = "Hello %s,\n\nYou requested a password reset. Visit this link to set a new password:\n%s\n\nThis link expires in 30 minutes.\n\nBest regards,\nUltiCode Team"
                .formatted(account.username(), resetUrl);

        emailService.sendSecurityEmail(account.email(), "UltiCode - Password Reset", htmlBody, textBody);
        log.info("Password reset email sent for account: {}", account.id());
    }

    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_INVALID_RESET_TOKEN);
        }

        long nowEpochMs = LocalDateTime.now(clock).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        List<AuthAccountRecord> candidates = accountPort.findUsersWithActivePasswordReset(nowEpochMs);

        AuthAccountRecord matchedAccount = null;
        for (AuthAccountRecord candidate : candidates) {
            Optional<AuthAccountPort.PasswordResetRecord> resetRecord = accountPort.findPasswordReset(candidate.id());
            if (resetRecord.isPresent() && passwordEncoder.matches(token, resetRecord.get().token())) {
                matchedAccount = candidate;
                break;
            }
        }

        if (matchedAccount == null) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_INVALID_RESET_TOKEN, "Invalid or expired reset token");
        }

        accountPort.updatePassword(matchedAccount.id(), passwordEncoder.encode(newPassword));
        accountPort.clearPasswordReset(matchedAccount.id());

        refreshTokenService.revokeAllUserTokens(matchedAccount.id());
        log.info("Password reset completed for account: {}", matchedAccount.id());
    }
}
