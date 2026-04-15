package com.ulticode.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.auth.dto.LoginDTO;
import com.ulticode.modules.auth.dto.LoginResponse;
import com.ulticode.modules.auth.dto.RegisterDTO;
import com.ulticode.modules.user.dto.UserVO;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import com.ulticode.modules.user.service.UserService;
import com.ulticode.security.csrf.CsrfService;
import com.ulticode.security.jwt.JwtProperties;
import com.ulticode.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl")
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserService userService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Spy
    private JwtProperties jwtProperties = new JwtProperties();

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CsrfService csrfService;

    @InjectMocks
    private AuthServiceImpl authService;

    private static final String USER_ID = "test-user-123";
    private static final String USERNAME = "testuser";
    private static final String PASSWORD = "password123";
    private static final String ACCESS_TOKEN = "access-token-value";
    private static final String REFRESH_TOKEN = "refresh-token-value";
    private static final String CSRF_TOKEN = "csrf-token-value";

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

    private HttpServletResponse mockResponse() {
        return mock(HttpServletResponse.class);
    }

    @Nested
    @DisplayName("login()")
    class LoginTests {

        @Test
        @DisplayName("successful login returns response with csrf token")
        void login_validCredentials_returnsLoginResponse() {
            // Arrange
            LoginDTO loginDTO = new LoginDTO();
            loginDTO.setUsername(USERNAME);
            loginDTO.setPassword(PASSWORD);

            User user = createActiveUser();
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
            when(passwordEncoder.matches(PASSWORD, user.getPassword())).thenReturn(true);
            when(jwtTokenProvider.generateAccessToken(USER_ID, USERNAME, "USER"))
                    .thenReturn(ACCESS_TOKEN);
            when(jwtTokenProvider.generateRefreshToken(USER_ID))
                    .thenReturn(REFRESH_TOKEN);
            when(csrfService.generateToken(USER_ID)).thenReturn(CSRF_TOKEN);
            when(userService.toVO(user)).thenReturn(mock(UserVO.class));

            // Act
            LoginResponse response = authService.login(loginDTO, mockResponse());

            // Assert
            assertThat(response.getCsrfToken()).isEqualTo(CSRF_TOKEN);
            assertThat(response.getUser()).isNotNull();
            verify(jwtTokenProvider).generateAccessToken(USER_ID, USERNAME, "USER");
            verify(csrfService).generateToken(USER_ID);
            verify(userService).updateLastLoginAt(USER_ID);
        }

        @Test
        @DisplayName("non-existent user throws AUTH_INVALID_CREDENTIALS")
        void login_nonExistentUser_throwsException() {
            LoginDTO loginDTO = new LoginDTO();
            loginDTO.setUsername(USERNAME);
            loginDTO.setPassword(PASSWORD);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            assertThatThrownBy(() -> authService.login(loginDTO, mockResponse()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
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

            assertThatThrownBy(() -> authService.login(loginDTO, mockResponse()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
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

            assertThatThrownBy(() -> authService.login(loginDTO, mockResponse()))
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

            assertThatThrownBy(() -> authService.login(loginDTO, mockResponse()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
        }
    }

    @Nested
    @DisplayName("register()")
    class RegisterTests {

        @Test
        @DisplayName("valid registration delegates to login and returns response")
        void register_validRegistration_delegatesToLogin() {
            // Arrange
            RegisterDTO registerDTO = new RegisterDTO();
            registerDTO.setUsername(USERNAME);
            registerDTO.setPassword(PASSWORD);
            registerDTO.setEmail("test@example.com");

            // selectCount returns 0 for both username and email checks
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(passwordEncoder.encode(PASSWORD)).thenReturn("encoded-password");

            // For the internal login call
            User user = createActiveUser();
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
            when(passwordEncoder.matches(PASSWORD, "encoded-password")).thenReturn(true);
            when(jwtTokenProvider.generateAccessToken(anyString(), anyString(), anyString()))
                    .thenReturn(ACCESS_TOKEN);
            when(jwtTokenProvider.generateRefreshToken(anyString())).thenReturn(REFRESH_TOKEN);
            when(csrfService.generateToken(anyString())).thenReturn(CSRF_TOKEN);
            when(userService.toVO(user)).thenReturn(mock(UserVO.class));

            // Act
            LoginResponse response = authService.register(registerDTO, mockResponse());

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getCsrfToken()).isEqualTo(CSRF_TOKEN);
            verify(userMapper).insert(any(User.class));
        }

        @Test
        @DisplayName("duplicate username throws AUTH_USERNAME_TAKEN")
        void register_duplicateUsername_throwsException() {
            RegisterDTO registerDTO = new RegisterDTO();
            registerDTO.setUsername(USERNAME);
            registerDTO.setPassword(PASSWORD);
            registerDTO.setEmail("test@example.com");

            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            assertThatThrownBy(() -> authService.register(registerDTO, mockResponse()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.AUTH_USERNAME_TAKEN));
        }

        @Test
        @DisplayName("duplicate email throws AUTH_EMAIL_TAKEN")
        void register_duplicateEmail_throwsException() {
            RegisterDTO registerDTO = new RegisterDTO();
            registerDTO.setUsername(USERNAME);
            registerDTO.setPassword(PASSWORD);
            registerDTO.setEmail("test@example.com");

            // First selectCount for username returns 0, second for email returns 1
            when(userMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(0L)
                    .thenReturn(1L);

            assertThatThrownBy(() -> authService.register(registerDTO, mockResponse()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.AUTH_EMAIL_TAKEN));
        }
    }

    @Nested
    @DisplayName("refresh()")
    class RefreshTests {

        @Test
        @DisplayName("valid refresh token returns new tokens")
        void refresh_validToken_returnsNewTokens() {
            User user = createActiveUser();

            when(jwtTokenProvider.getUserIdFromToken(REFRESH_TOKEN)).thenReturn(USER_ID);
            when(userMapper.selectById(USER_ID)).thenReturn(user);
            when(jwtTokenProvider.generateAccessToken(USER_ID, USERNAME, "USER"))
                    .thenReturn(ACCESS_TOKEN);
            when(jwtTokenProvider.generateRefreshToken(USER_ID)).thenReturn("new-refresh-token");
            when(csrfService.generateToken(USER_ID)).thenReturn(CSRF_TOKEN);
            when(userService.toVO(user)).thenReturn(mock(UserVO.class));

            LoginResponse response = authService.refresh(REFRESH_TOKEN, mockResponse());

            assertThat(response).isNotNull();
            assertThat(response.getCsrfToken()).isEqualTo(CSRF_TOKEN);
            verify(jwtTokenProvider).generateAccessToken(USER_ID, USERNAME, "USER");
            verify(csrfService).generateToken(USER_ID);
        }

        @Test
        @DisplayName("null refresh token throws AUTH_TOKEN_EXPIRED")
        void refresh_nullToken_throwsException() {
            assertThatThrownBy(() -> authService.refresh(null, mockResponse()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.AUTH_TOKEN_EXPIRED));
        }

        @Test
        @DisplayName("blank refresh token throws AUTH_TOKEN_EXPIRED")
        void refresh_blankToken_throwsException() {
            assertThatThrownBy(() -> authService.refresh("   ", mockResponse()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.AUTH_TOKEN_EXPIRED));
        }

        @Test
        @DisplayName("refresh token returning null userId throws AUTH_TOKEN_EXPIRED")
        void refresh_invalidToken_throwsException() {
            when(jwtTokenProvider.getUserIdFromToken(REFRESH_TOKEN)).thenReturn(null);

            assertThatThrownBy(() -> authService.refresh(REFRESH_TOKEN, mockResponse()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.AUTH_TOKEN_EXPIRED));
        }
    }
}
