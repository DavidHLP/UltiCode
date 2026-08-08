package com.ulticode.auth.service;

import com.ulticode.auth.account.AuthAccountPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.error.AuthErrorCode;
import com.ulticode.auth.mail.AuthEmailService;
import com.ulticode.auth.refreshtoken.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PasswordResetWorkflow (backend-auth)")
class PasswordResetWorkflowTest {

    private static final Instant NOW = Instant.parse("2026-08-06T00:00:00Z");
    private static final long NOW_EPOCH_MS = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli();

    @Mock
    private AuthAccountPort accountPort;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuthEmailService emailService;

    private PasswordResetWorkflow passwordResetWorkflow;
    private AuthAccountRecord user;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        DefaultPasswordResetWorkflow implementation = new DefaultPasswordResetWorkflow(
                accountPort, passwordEncoder, refreshTokenService, emailService, clock);
        ReflectionTestUtils.setField(implementation, "frontendUrl", "http://localhost:9002");
        passwordResetWorkflow = implementation;

        user = new AuthAccountRecord(
                "user-123", "alice", "alice@example.com", "old-pass-hash", "USER",
                true, false, null, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
        );
    }

    @Nested
    @DisplayName("forgotPassword()")
    class ForgotPassword {

        @Test
        @DisplayName("ignores blank email without touching account or mail storage")
        void ignoresBlankEmail() {
            passwordResetWorkflow.forgotPassword("  ");

            verifyNoInteractions(accountPort, passwordEncoder, refreshTokenService, emailService);
        }

        @Test
        @DisplayName("silently returns for an unknown account to prevent enumeration")
        void silentNoOpForUnknownAccount() {
            when(accountPort.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

            passwordResetWorkflow.forgotPassword("unknown@example.com");

            verify(accountPort).findByEmail("unknown@example.com");
            verify(accountPort, never()).savePasswordReset(anyString(), anyString(), anyLong());
            verifyNoInteractions(passwordEncoder, refreshTokenService, emailService);
        }

        @Test
        @DisplayName("stores a hashed token with the existing 30-minute expiry and sends email")
        void storesTokenAndSendsEmail() {
            when(accountPort.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.encode(anyString())).thenReturn("hashed-token-123");

            passwordResetWorkflow.forgotPassword("alice@example.com");

            verify(accountPort).savePasswordReset(
                    eq("user-123"), eq("hashed-token-123"), eq(NOW_EPOCH_MS + 30 * 60 * 1000L));
            verify(emailService).sendSecurityEmail(
                    eq("alice@example.com"), eq("UltiCode - Password Reset"), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("resetPassword()")
    class ResetPassword {

        @Test
        @DisplayName("rejects a missing token before querying reset storage")
        void rejectsMissingToken() {
            assertThatThrownBy(() -> passwordResetWorkflow.resetPassword(" ", "NewPass123"))
                    .isInstanceOf(AuthBusinessException.class)
                    .satisfies(ex -> assertThat(((AuthBusinessException) ex).getErrorCode())
                            .isEqualTo(AuthErrorCode.AUTH_INVALID_RESET_TOKEN));

            verifyNoInteractions(accountPort, passwordEncoder, refreshTokenService, emailService);
        }

        @Test
        @DisplayName("rejects an expired token without changing password or sessions")
        void rejectsExpiredToken() {
            when(accountPort.findUsersWithActivePasswordReset(NOW_EPOCH_MS)).thenReturn(List.of());

            assertInvalidResetToken("expired-token");

            verify(accountPort).findUsersWithActivePasswordReset(NOW_EPOCH_MS);
            verify(accountPort, never()).updatePassword(anyString(), anyString());
            verify(refreshTokenService, never()).revokeAllUserTokens(anyString());
        }

        @Test
        @DisplayName("rejects an invalid token without changing password or sessions")
        void rejectsInvalidToken() {
            givenCandidate(false);

            assertInvalidResetToken("invalid-token");

            verify(accountPort, never()).updatePassword(anyString(), anyString());
            verify(accountPort, never()).clearPasswordReset(anyString());
            verify(refreshTokenService, never()).revokeAllUserTokens(anyString());
        }

        @Test
        @DisplayName("replaces password, clears the single-use token, and revokes all sessions")
        void successfulReset() {
            givenCandidate(true);
            when(passwordEncoder.encode("NewPass123")).thenReturn("new-pass-hash");

            passwordResetWorkflow.resetPassword("raw-token-123", "NewPass123");

            verify(accountPort).updatePassword("user-123", "new-pass-hash");
            verify(accountPort, times(1)).clearPasswordReset("user-123");
            verify(refreshTokenService, times(1)).revokeAllUserTokens("user-123");
        }

        private void givenCandidate(boolean tokenMatches) {
            when(accountPort.findUsersWithActivePasswordReset(NOW_EPOCH_MS)).thenReturn(List.of(user));
            AuthAccountPort.PasswordResetRecord resetRecord = new AuthAccountPort.PasswordResetRecord(
                    "user-123", "hashed-token-123", NOW_EPOCH_MS + 60_000L);
            when(accountPort.findPasswordReset("user-123")).thenReturn(Optional.of(resetRecord));
            when(passwordEncoder.matches(anyString(), eq("hashed-token-123"))).thenReturn(tokenMatches);
        }

        private void assertInvalidResetToken(String token) {
            assertThatThrownBy(() -> passwordResetWorkflow.resetPassword(token, "NewPass123"))
                    .isInstanceOf(AuthBusinessException.class)
                    .satisfies(ex -> assertThat(((AuthBusinessException) ex).getErrorCode())
                            .isEqualTo(AuthErrorCode.AUTH_INVALID_RESET_TOKEN));
        }
    }
}
