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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PasswordResetService (backend-auth)")
class PasswordResetServiceTest {

    @Mock
    private AuthAccountPort accountPort;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuthEmailService emailService;

    @Mock
    private Clock clock;

    private PasswordResetService passwordResetService;

    private AuthAccountRecord user;

    @BeforeEach
    void setUp() {
        lenient().when(clock.getZone()).thenReturn(ZoneId.systemDefault());
        lenient().when(clock.instant()).thenReturn(Instant.now());

        passwordResetService = new PasswordResetService(
                accountPort, passwordEncoder, refreshTokenService, emailService, clock);

        user = new AuthAccountRecord(
                "user-123", "alice", "alice@example.com", "old-pass-hash", "USER",
                true, false, null, LocalDateTime.now()
        );
    }

    @Nested
    @DisplayName("forgotPassword()")
    class ForgotPassword {

        @Test
        @DisplayName("sends password reset email for existing user")
        void sendsEmailForExistingUser() {
            when(accountPort.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.encode(anyString())).thenReturn("hashed-token-123");

            passwordResetService.forgotPassword("alice@example.com");

            verify(accountPort, times(1)).savePasswordReset(eq("user-123"), eq("hashed-token-123"), anyLong());
            verify(emailService, times(1)).sendSecurityEmail(eq("alice@example.com"), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("silently returns without error when email does not exist (prevents enumeration)")
        void silentNoOpForNonExistentEmail() {
            when(accountPort.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

            passwordResetService.forgotPassword("unknown@example.com");

            verify(accountPort, never()).savePasswordReset(anyString(), anyString(), anyLong());
            verify(emailService, never()).sendSecurityEmail(anyString(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("resetPassword()")
    class ResetPassword {

        @Test
        @DisplayName("resets password and revokes sessions when token matches candidate")
        void successfulReset() {
            when(accountPort.findUsersWithActivePasswordReset(anyLong())).thenReturn(List.of(user));
            AuthAccountPort.PasswordResetRecord resetRecord =
                    new AuthAccountPort.PasswordResetRecord("user-123", "hashed-token-123", System.currentTimeMillis() + 60000);
            when(accountPort.findPasswordReset("user-123")).thenReturn(Optional.of(resetRecord));
            when(passwordEncoder.matches("raw-token-123", "hashed-token-123")).thenReturn(true);
            when(passwordEncoder.encode("NewPass123")).thenReturn("new-pass-hash");

            passwordResetService.resetPassword("raw-token-123", "NewPass123");

            verify(accountPort, times(1)).updatePassword("user-123", "new-pass-hash");
            verify(accountPort, times(1)).clearPasswordReset("user-123");
            verify(refreshTokenService, times(1)).revokeAllUserTokens("user-123");
        }

        @Test
        @DisplayName("throws AUTH_INVALID_RESET_TOKEN when no candidate token matches")
        void throwsOnInvalidToken() {
            when(accountPort.findUsersWithActivePasswordReset(anyLong())).thenReturn(List.of(user));
            AuthAccountPort.PasswordResetRecord resetRecord =
                    new AuthAccountPort.PasswordResetRecord("user-123", "hashed-token-123", System.currentTimeMillis() + 60000);
            when(accountPort.findPasswordReset("user-123")).thenReturn(Optional.of(resetRecord));
            when(passwordEncoder.matches("invalid-token", "hashed-token-123")).thenReturn(false);

            assertThatThrownBy(() -> passwordResetService.resetPassword("invalid-token", "NewPass123"))
                    .isInstanceOf(AuthBusinessException.class)
                    .satisfies(ex -> assertThat(((AuthBusinessException) ex).getErrorCode())
                            .isEqualTo(AuthErrorCode.AUTH_INVALID_RESET_TOKEN));

            verify(accountPort, never()).updatePassword(anyString(), anyString());
            verify(refreshTokenService, never()).revokeAllUserTokens(anyString());
        }
    }
}
