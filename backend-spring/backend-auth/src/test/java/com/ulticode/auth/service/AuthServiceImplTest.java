package com.ulticode.auth.service;

import com.ulticode.auth.account.AuthAccountPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.dto.LoginDTO;
import com.ulticode.auth.dto.LoginResponse;
import com.ulticode.auth.dto.RegisterDTO;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.error.AuthErrorCode;
import com.ulticode.auth.refreshtoken.service.RefreshTokenService;
import com.ulticode.auth.service.impl.AuthServiceImpl;
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
@DisplayName("AuthServiceImpl (backend-auth)")
class AuthServiceImplTest {

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

    private AuthService authService;

    private AuthAccountRecord activeUser;

    @BeforeEach
    void setUp() {
        lenient().when(clock.getZone()).thenReturn(ZoneId.systemDefault());
        lenient().when(clock.instant()).thenReturn(Instant.now());

        authService = new AuthServiceImpl(
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
            LoginDTO dto = new LoginDTO();
            dto.setUsername("alice");
            dto.setPassword("secret123");

            when(accountPort.findByUsername("alice")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("secret123", "encoded-pass")).thenReturn(true);

            LoginResponse mockResponse = LoginResponse.builder()
                    .csrfToken("csrf-token-123")
                    .user(new UserIdentityDTO("user-123", "alice", "USER", true, false))
                    .build();
            when(authSessionPort.completeLogin(eq(activeUser), any())).thenReturn(mockResponse);

            LoginResponse response = authService.login(dto, null);

            assertThat(response).isNotNull();
            assertThat(response.getCsrfToken()).isEqualTo("csrf-token-123");
            verify(accountPort, times(1)).updateLastLoginAt("user-123");
        }

        @Test
        @DisplayName("throws AUTH_INVALID_CREDENTIALS when user not found")
        void userNotFound() {
            LoginDTO dto = new LoginDTO();
            dto.setUsername("unknown");
            dto.setPassword("secret123");

            when(accountPort.findByUsername("unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(dto, null))
                    .isInstanceOf(AuthBusinessException.class)
                    .satisfies(ex -> assertThat(((AuthBusinessException) ex).getErrorCode())
                            .isEqualTo(AuthErrorCode.AUTH_INVALID_CREDENTIALS));
        }

        @Test
        @DisplayName("throws AUTH_INVALID_CREDENTIALS when password fails")
        void passwordMismatch() {
            LoginDTO dto = new LoginDTO();
            dto.setUsername("alice");
            dto.setPassword("wrongpass");

            when(accountPort.findByUsername("alice")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("wrongpass", "encoded-pass")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(dto, null))
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
            RegisterDTO dto = new RegisterDTO();
            dto.setUsername("bob");
            dto.setPassword("Pass1234");
            dto.setEmail("bob@example.com");

            when(accountPort.findByUsername("bob")).thenReturn(Optional.empty());
            when(accountPort.findByEmail("bob@example.com")).thenReturn(Optional.empty());
            when(passwordEncoder.encode("Pass1234")).thenReturn("encoded-pass-bob");
            when(accountPort.create(any())).thenAnswer(invocation -> invocation.getArgument(0));

            LoginResponse mockResponse = LoginResponse.builder()
                    .csrfToken("csrf-bob")
                    .user(new UserIdentityDTO("fixed-uuid-1234", "bob", "USER", true, false))
                    .build();
            when(authSessionPort.completeLogin(any(), any())).thenReturn(mockResponse);

            LoginResponse response = authService.register(dto, null);

            assertThat(response).isNotNull();
            assertThat(response.getCsrfToken()).isEqualTo("csrf-bob");
            verify(accountPort, times(1)).create(any());
        }

        @Test
        @DisplayName("throws AUTH_USERNAME_TAKEN when username exists")
        void usernameTaken() {
            RegisterDTO dto = new RegisterDTO();
            dto.setUsername("alice");
            dto.setPassword("Pass1234");

            when(accountPort.findByUsername("alice")).thenReturn(Optional.of(activeUser));

            assertThatThrownBy(() -> authService.register(dto, null))
                    .isInstanceOf(AuthBusinessException.class)
                    .satisfies(ex -> assertThat(((AuthBusinessException) ex).getErrorCode())
                            .isEqualTo(AuthErrorCode.AUTH_USERNAME_TAKEN));
        }
    }

    @Nested
    @DisplayName("refresh()")
    class Refresh {

        @Test
        @DisplayName("successful refresh validates token and completes refresh")
        void successfulRefresh() {
            RefreshTokenService.RotationResult rotation = new RefreshTokenService.RotationResult("user-123", "new-refresh-token");
            when(refreshTokenService.validateAndRotate("old-token")).thenReturn(rotation);
            when(accountPort.findById("user-123")).thenReturn(Optional.of(activeUser));

            LoginResponse mockResponse = LoginResponse.builder()
                    .csrfToken("csrf-refreshed")
                    .user(new UserIdentityDTO("user-123", "alice", "USER", true, false))
                    .build();
            when(authSessionPort.completeRefresh(eq(activeUser), eq("new-refresh-token"), any())).thenReturn(mockResponse);

            LoginResponse response = authService.refresh("old-token", null);

            assertThat(response).isNotNull();
            assertThat(response.getCsrfToken()).isEqualTo("csrf-refreshed");
        }
    }

    @Nested
    @DisplayName("logout()")
    class Logout {

        @Test
        @DisplayName("revokes token and clears session")
        void logout() {
            authService.logout("token-xyz", null);

            verify(refreshTokenService, times(1)).revokePresentedToken("token-xyz");
            verify(authSessionPort, times(1)).clearSession(any());
        }
    }
}
