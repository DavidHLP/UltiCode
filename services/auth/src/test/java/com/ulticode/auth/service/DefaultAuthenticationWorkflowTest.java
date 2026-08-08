package com.ulticode.auth.service;

import com.ulticode.auth.account.AuthAccountPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.dto.AuthUserVO;
import com.ulticode.auth.dto.LoginResponse;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.error.AuthErrorCode;
import com.ulticode.auth.refreshtoken.service.RefreshTokenService;
import com.ulticode.auth.session.AuthSession;
import com.ulticode.auth.session.AuthSessionPort;
import com.ulticode.auth.util.FixedUuidGenerator;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthenticationWorkflow implementation (backend-auth)")
class DefaultAuthenticationWorkflowTest {

    @Mock
    private AuthAccountPort accountPort;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuthSessionPort authSessionPort;

    @Mock
    private Clock clock;

    private AuthenticationWorkflow authenticationWorkflow;

    private AuthAccountRecord activeUser;

    @BeforeEach
    void setUp() {
        lenient().when(clock.getZone()).thenReturn(ZoneId.systemDefault());
        lenient().when(clock.instant()).thenReturn(Instant.now());
        authenticationWorkflow = new DefaultAuthenticationWorkflow(
                accountPort, passwordEncoder, refreshTokenService, authSessionPort,
                new FixedUuidGenerator(), clock);

        activeUser = new AuthAccountRecord(
                "user-123", "alice", "alice@example.com", "encoded-pass", "USER",
                true, false, null, LocalDateTime.now()
        );
    }

    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("successful login with matching credentials")
        void successfulLogin() {
            when(accountPort.findByUsername("alice")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("secret123", "encoded-pass")).thenReturn(true);
            when(authSessionPort.completeLogin(eq(activeUser))).thenReturn(mockSession("csrf-token-123"));

            AuthSession session = authenticationWorkflow.login("alice", "secret123");

            assertThat(session.response()).isNotNull();
            assertThat(session.response().getCsrfToken()).isEqualTo("csrf-token-123");
            verify(accountPort, times(1)).updateLastLoginAt("user-123");
        }

        @Test
        @DisplayName("throws AUTH_INVALID_CREDENTIALS when user not found")
        void userNotFound() {
            when(accountPort.findByUsername("unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authenticationWorkflow.login("unknown", "secret123"))
                    .isInstanceOf(AuthBusinessException.class)
                    .satisfies(ex -> assertThat(((AuthBusinessException) ex).getErrorCode())
                            .isEqualTo(AuthErrorCode.AUTH_INVALID_CREDENTIALS));
        }

        @Test
        @DisplayName("throws AUTH_INVALID_CREDENTIALS when password fails")
        void passwordMismatch() {
            when(accountPort.findByUsername("alice")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("wrongpass", "encoded-pass")).thenReturn(false);

            assertThatThrownBy(() -> authenticationWorkflow.login("alice", "wrongpass"))
                    .isInstanceOf(AuthBusinessException.class)
                    .satisfies(ex -> assertThat(((AuthBusinessException) ex).getErrorCode())
                            .isEqualTo(AuthErrorCode.AUTH_INVALID_CREDENTIALS));
        }
    }

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("successful registration creates account and completes login")
        void successfulRegistration() {
            when(accountPort.findByUsername("bob")).thenReturn(Optional.empty());
            when(accountPort.findByEmail("bob@example.com")).thenReturn(Optional.empty());
            when(passwordEncoder.encode("Pass1234")).thenReturn("encoded-pass-bob");
            when(accountPort.create(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(authSessionPort.completeLogin(any())).thenReturn(mockSession("csrf-bob"));

            AuthSession session = authenticationWorkflow.register("bob", "bob@example.com", "Pass1234");

            assertThat(session.response()).isNotNull();
            assertThat(session.response().getCsrfToken()).isEqualTo("csrf-bob");
            verify(accountPort, times(1)).create(any());
        }

        @Test
        @DisplayName("throws AUTH_USERNAME_TAKEN when username exists")
        void usernameTaken() {
            when(accountPort.findByUsername("alice")).thenReturn(Optional.of(activeUser));

            assertThatThrownBy(() -> authenticationWorkflow.register("alice", null, "Pass1234"))
                    .isInstanceOf(AuthBusinessException.class)
                    .satisfies(ex -> assertThat(((AuthBusinessException) ex).getErrorCode())
                            .isEqualTo(AuthErrorCode.AUTH_USERNAME_TAKEN));
        }
    }

    @Nested
    @DisplayName("refresh()")
    class Refresh {

        @Test
        @DisplayName("missing refresh credential is rejected")
        void missingRefreshCredential() {
            assertThatThrownBy(() -> authenticationWorkflow.refresh(" "))
                    .isInstanceOf(AuthBusinessException.class)
                    .satisfies(ex -> assertThat(((AuthBusinessException) ex).getErrorCode())
                            .isEqualTo(AuthErrorCode.AUTH_TOKEN_EXPIRED));
        }

        @Test
        @DisplayName("successful refresh validates token and completes refresh")
        void successfulRefresh() {
            RefreshTokenService.RotationResult rotation =
                    new RefreshTokenService.RotationResult("user-123", "new-refresh-token");
            when(refreshTokenService.validateAndRotate("old-token")).thenReturn(rotation);
            when(accountPort.findById("user-123")).thenReturn(Optional.of(activeUser));
            when(authSessionPort.completeRefresh(eq(activeUser), eq("new-refresh-token")))
                    .thenReturn(mockSession("csrf-refreshed"));

            AuthSession session = authenticationWorkflow.refresh("old-token");

            assertThat(session.response()).isNotNull();
            assertThat(session.response().getCsrfToken()).isEqualTo("csrf-refreshed");
        }
    }

    @Nested
    @DisplayName("logout()")
    class Logout {

        @Test
        @DisplayName("revokes token and returns clear-session mutations")
        void logout() {
            when(authSessionPort.clearSession()).thenReturn(new AuthSession(null, List.of()));

            AuthSession session = authenticationWorkflow.logout("token-xyz");

            assertThat(session.response()).isNull();
            verify(refreshTokenService, times(1)).revokePresentedToken("token-xyz");
            verify(authSessionPort, times(1)).clearSession();
        }
    }

    private static AuthSession mockSession(String csrfToken) {
        return new AuthSession(
                LoginResponse.builder()
                        .csrfToken(csrfToken)
                        .user(new AuthUserVO("user-123", "alice", "alice", "", "USER", true, false, ""))
                        .build(),
                List.of()
        );
    }
}
