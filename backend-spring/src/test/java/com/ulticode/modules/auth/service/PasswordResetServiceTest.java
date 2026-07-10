package com.ulticode.modules.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.email.dto.SendEmailDTO;
import com.ulticode.modules.email.service.EmailService;
import com.ulticode.modules.refreshtoken.service.RefreshTokenService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.auth.account.AuthAccountPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordResetService")
class PasswordResetServiceTest {

    @Mock
    private AuthAccountPort accountPort;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private EmailService emailService;

    @Mock
    private Clock clock;

    @InjectMocks
    private PasswordResetService passwordResetService;

    private static final String USER_ID = "user-1";
    private static final String EMAIL = "test@example.com";
    private static final String PLAIN_TOKEN = "plain-token-uuid";
    private static final String HASHED_TOKEN = "$2a$10$somehash";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(passwordResetService, "frontendUrl", "http://localhost:9002");
        lenient().when(clock.instant()).thenReturn(Instant.now());
        lenient().when(clock.getZone()).thenReturn(ZoneId.systemDefault());
    }

    private User createUserWithResetToken() {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("testuser");
        user.setEmail(EMAIL);
        user.setPassword("old-encoded-password");
        user.setPasswordResetTokenHash(HASHED_TOKEN);
        user.setPasswordResetExpiresAt(LocalDateTime.now().plusMinutes(15));
        return user;
    }

    @Nested
    @DisplayName("forgotPassword()")
    class ForgotPasswordTests {

        @Test
        @DisplayName("non-existent email returns silently (security)")
        void forgotPassword_nonExistentEmail_returnsSilently() {
            when(accountPort.findByEmail(anyString())).thenReturn(Optional.empty());

            passwordResetService.forgotPassword("nonexistent@example.com");

            verify(emailService, never()).sendEmail(any(SendEmailDTO.class));
        }

        @Test
        @DisplayName("existing user stores token hash and sends email")
        void forgotPassword_existingUser_sendsEmail() {
            User user = new User();
            user.setId(USER_ID);
            user.setUsername("testuser");
            user.setEmail(EMAIL);

            when(accountPort.findByEmail(anyString())).thenReturn(Optional.ofNullable(user));
            when(passwordEncoder.encode(anyString())).thenReturn(HASHED_TOKEN);

            passwordResetService.forgotPassword(EMAIL);

            verify(emailService).sendEmail(any(SendEmailDTO.class));
            verify(accountPort).savePasswordReset(eq(user.getId()), eq(HASHED_TOKEN), anyLong());
        }
    }

    @Nested
    @DisplayName("resetPassword()")
    class ResetPasswordTests {

        @Test
        @DisplayName("valid token resets password and revokes sessions")
        void resetPassword_validToken_resetsAndRevokes() {
            User user = createUserWithResetToken();
            when(accountPort.findUsersWithActivePasswordReset(anyLong())).thenReturn(List.of(user));
            when(passwordEncoder.matches(PLAIN_TOKEN, HASHED_TOKEN)).thenReturn(true);
            when(passwordEncoder.encode("newPassword123")).thenReturn("new-hash");

            passwordResetService.resetPassword(PLAIN_TOKEN, "newPassword123");

            // The auth service no longer mutates the in-memory user — it
            // delegates persistence to the AuthAccountPort. Verify the
            // port calls instead of the local object's fields.
            verify(accountPort).updatePassword(eq(user.getId()), eq("new-hash"));
            verify(accountPort).clearPasswordReset(user.getId());
            verify(refreshTokenService).revokeAllUserTokens(USER_ID);
        }

        @Test
        @DisplayName("invalid token throws AUTH_INVALID_RESET_TOKEN")
        void resetPassword_invalidToken_throwsException() {
            // No users with valid tokens
            when(accountPort.findUsersWithActivePasswordReset(anyLong())).thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> passwordResetService.resetPassword("invalid-token", "newPassword123"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.AUTH_INVALID_RESET_TOKEN));
        }

        @Test
        @DisplayName("expired token (no matching candidates) throws AUTH_INVALID_RESET_TOKEN")
        void resetPassword_expiredToken_throwsException() {
            // Token expired -- selectList returns empty because gt(expiresAt, now) filters it out
            when(accountPort.findUsersWithActivePasswordReset(anyLong())).thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> passwordResetService.resetPassword("expired-token", "newPassword123"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.AUTH_INVALID_RESET_TOKEN));
        }

        @Test
        @DisplayName("token with wrong value does not match any candidate")
        void resetPassword_wrongTokenValue_throwsException() {
            User user = createUserWithResetToken();
            when(accountPort.findUsersWithActivePasswordReset(anyLong())).thenReturn(List.of(user));
            when(passwordEncoder.matches("wrong-token-value", HASHED_TOKEN)).thenReturn(false);

            assertThatThrownBy(() -> passwordResetService.resetPassword("wrong-token-value", "newPassword123"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.AUTH_INVALID_RESET_TOKEN));
        }
    }
}
