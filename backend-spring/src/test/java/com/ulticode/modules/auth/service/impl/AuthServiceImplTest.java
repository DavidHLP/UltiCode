package com.ulticode.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.auth.dto.LoginDTO;
import com.ulticode.modules.auth.dto.LoginResponse;
import com.ulticode.modules.auth.dto.RegisterDTO;
import com.ulticode.modules.auth.session.AuthSessionPort;
import com.ulticode.modules.user.dto.UserVO;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import com.ulticode.modules.user.service.UserService;
import com.ulticode.modules.refreshtoken.service.RefreshTokenService;
import com.ulticode.security.jwt.JwtTokenProvider;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthServiceImpl")
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserService userService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuthSessionPort authSessionPort;

    @Mock
    private Clock clock;

    @InjectMocks
    private AuthServiceImpl authService;

    private static final String USER_ID = "test-user-123";
    private static final String USERNAME = "testuser";
    private static final String PASSWORD = "password123";
    private static final String REFRESH_TOKEN = "refresh-token-value";

    @BeforeEach
    void setUp() {
        org.mockito.MockitoAnnotations.openMocks(this);
        lenient().when(clock.instant()).thenReturn(java.time.Instant.now());
        lenient().when(clock.getZone()).thenReturn(java.time.ZoneId.systemDefault());
    }

    private User createActiveUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername(USERNAME);
        user.setPassword("encoded-password");
        user.setRole("USER");
        user.setIsActive(true);
        user.setIsBanned(false);
        return user;
    }

    private LoginResponse stubbedSessionResponse() {
        return LoginResponse.builder()
                .csrfToken("csrf-from-session")
                .user(mock(UserVO.class))
                .build();
    }

    @Nested
    @DisplayName("login()")
    class LoginTests {

        @Test
        @DisplayName("successful login delegates to AuthSessionPort and returns response")
        void login_validCredentials_returnsLoginResponse() {
            // Arrange
            LoginDTO loginDTO = new LoginDTO();
            loginDTO.setUsername(USERNAME);
            loginDTO.setPassword(PASSWORD);

            User user = createActiveUser();
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
            when(passwordEncoder.matches(PASSWORD, user.getPassword())).thenReturn(true);
            LoginResponse expected = stubbedSessionResponse();
            when(authSessionPort.completeLogin(any(User.class), any())).thenReturn(expected);

            // Act
            LoginResponse response = authService.login(loginDTO, mock(HttpServletResponse.class));

            // Assert
            assertThat(response).isSameAs(expected);
            verify(authSessionPort).completeLogin(eq(user), any());
            verify(userService).updateLastLoginAt(USER_ID);
        }

        @Test
        @DisplayName("non-existent user throws AUTH_INVALID_CREDENTIALS")
        void login_nonExistentUser_throwsException() {
            LoginDTO loginDTO = new LoginDTO();
            loginDTO.setUsername(USERNAME);
            loginDTO.setPassword(PASSWORD);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            assertThatThrownBy(() -> authService.login(loginDTO, mock(HttpServletResponse.class)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
            verify(authSessionPort, never()).completeLogin(any(), any());
        }

        @Test
        @DisplayName("wrong password throws AUTH_INVALID_CREDENTIALS")
        void login_wrongPassword_throwsException() {
            LoginDTO loginDTO = new LoginDTO();
            loginDTO.setUsername(USERNAME);
            loginDTO.setPassword(PASSWORD);

            User user = createActiveUser();
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
            when(passwordEncoder.matches(PASSWORD, user.getPassword())).thenReturn(false);

            assertThatThrownBy(() -> authService.login(loginDTO, mock(HttpServletResponse.class)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
            verify(authSessionPort, never()).completeLogin(any(), any());
        }

        @Test
        @DisplayName("inactive user throws AUTH_INVALID_CREDENTIALS")
        void login_inactiveUser_throwsException() {
            LoginDTO loginDTO = new LoginDTO();
            loginDTO.setUsername(USERNAME);
            loginDTO.setPassword(PASSWORD);

            User user = createActiveUser();
            user.setIsActive(false);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
            when(passwordEncoder.matches(PASSWORD, user.getPassword())).thenReturn(true);

            assertThatThrownBy(() -> authService.login(loginDTO, mock(HttpServletResponse.class)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
        }

        @Test
        @DisplayName("banned user throws AUTH_INVALID_CREDENTIALS")
        void login_bannedUser_throwsException() {
            LoginDTO loginDTO = new LoginDTO();
            loginDTO.setUsername(USERNAME);
            loginDTO.setPassword(PASSWORD);

            User user = createActiveUser();
            user.setIsBanned(true);
            user.setBannedUntil(LocalDateTime.now().plusDays(1));
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
            when(passwordEncoder.matches(PASSWORD, user.getPassword())).thenReturn(true);

            assertThatThrownBy(() -> authService.login(loginDTO, mock(HttpServletResponse.class)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
        }
    }

    @Nested
    @DisplayName("register()")
    class RegisterTests {

        @Test
        @DisplayName("valid registration persists user, updates last login, and delegates to AuthSessionPort")
        void register_validRegistration_delegatesToLogin() {
            RegisterDTO registerDTO = new RegisterDTO();
            registerDTO.setUsername(USERNAME);
            registerDTO.setPassword(PASSWORD);
            registerDTO.setEmail("test@example.com");

            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(passwordEncoder.encode(PASSWORD)).thenReturn("encoded-password");

            LoginResponse expected = stubbedSessionResponse();
            when(authSessionPort.completeLogin(any(User.class), any())).thenReturn(expected);

            // Act
            LoginResponse response = authService.register(registerDTO, mock(HttpServletResponse.class));

            // Assert
            assertThat(response).isSameAs(expected);
            verify(userMapper).insert(any(User.class));
            verify(userService).updateLastLoginAt(anyString());
        }

        @Test
        @DisplayName("duplicate username throws AUTH_USERNAME_TAKEN")
        void register_duplicateUsername_throwsException() {
            RegisterDTO registerDTO = new RegisterDTO();
            registerDTO.setUsername(USERNAME);
            registerDTO.setPassword(PASSWORD);
            registerDTO.setEmail("test@example.com");

            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            assertThatThrownBy(() -> authService.register(registerDTO, mock(HttpServletResponse.class)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.AUTH_USERNAME_TAKEN));
            verify(userMapper, never()).insert(any(User.class));
            verify(authSessionPort, never()).completeLogin(any(), any());
        }

        @Test
        @DisplayName("duplicate email throws AUTH_EMAIL_TAKEN")
        void register_duplicateEmail_throwsException() {
            RegisterDTO registerDTO = new RegisterDTO();
            registerDTO.setUsername(USERNAME);
            registerDTO.setPassword(PASSWORD);
            registerDTO.setEmail("test@example.com");

            when(userMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(0L)
                    .thenReturn(1L);

            assertThatThrownBy(() -> authService.register(registerDTO, mock(HttpServletResponse.class)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.AUTH_EMAIL_TAKEN));
            verify(userMapper, never()).insert(any(User.class));
        }
    }

    @Nested
    @DisplayName("refresh()")
    class RefreshTests {

        @Test
        @DisplayName("valid refresh token returns session response from deep module")
        void refresh_validToken_returnsNewTokens() {
            User user = createActiveUser();

            when(refreshTokenService.validateAndRotate(REFRESH_TOKEN))
                    .thenReturn(new RefreshTokenService.RotationResult(USER_ID, "new-refresh-token"));
            when(userMapper.selectById(USER_ID)).thenReturn(user);
            LoginResponse expected = stubbedSessionResponse();
            when(authSessionPort.completeRefresh(any(User.class), eq("new-refresh-token"), any())).thenReturn(expected);

            LoginResponse response = authService.refresh(REFRESH_TOKEN, mock(HttpServletResponse.class));

            assertThat(response).isSameAs(expected);
            verify(authSessionPort).completeRefresh(eq(user), eq("new-refresh-token"), any());
        }

        @Test
        @DisplayName("expired JWT throws AUTH_INVALID_CREDENTIALS")
        void refresh_expiredJwt_throwsException() {
            when(refreshTokenService.validateAndRotate(REFRESH_TOKEN))
                    .thenThrow(new ExpiredJwtException(null, null, "expired"));

            assertThatThrownBy(() -> authService.refresh(REFRESH_TOKEN, mock(HttpServletResponse.class)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
            verify(authSessionPort, never()).completeRefresh(any(), anyString(), any());
        }

        @Test
        @DisplayName("refresh rotating to a missing user throws AUTH_USER_NOT_FOUND")
        void refresh_missingUser_throwsException() {
            when(refreshTokenService.validateAndRotate(REFRESH_TOKEN))
                    .thenReturn(new RefreshTokenService.RotationResult(USER_ID, "new-refresh-token"));
            when(userMapper.selectById(USER_ID)).thenReturn(null);

            assertThatThrownBy(() -> authService.refresh(REFRESH_TOKEN, mock(HttpServletResponse.class)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.AUTH_USER_NOT_FOUND));
        }
    }

    @Test
    @DisplayName("logout revokes the presented refresh token and clears session cookies")
    void logout_revokesPresentedToken() {
        HttpServletResponse response = mock(HttpServletResponse.class);

        authService.logout(REFRESH_TOKEN, response);

        verify(refreshTokenService).revokePresentedToken(REFRESH_TOKEN);
        verify(authSessionPort).clearSession(response);
    }
}
