package com.ulticode.modules.auth.service.impl;

import cn.hutool.core.util.IdUtil;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.auth.dto.LoginDTO;
import com.ulticode.modules.auth.dto.LoginResponse;
import com.ulticode.modules.auth.dto.RegisterDTO;
import com.ulticode.modules.auth.service.AuthService;
import com.ulticode.modules.user.dto.UserVO;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import com.ulticode.modules.user.service.UserService;
import com.ulticode.security.jwt.JwtProperties;
import com.ulticode.security.jwt.JwtTokenProvider;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Implementation of AuthService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;

    @Override
    public LoginResponse login(LoginDTO loginDTO, HttpServletResponse response) {
        // Find user by username
        User user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getUsername, loginDTO.getUsername())
        );

        if (user == null) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        // Verify password
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        // Check if user is active
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "Account is not active");
        }

        // Check if user is banned
        if (Boolean.TRUE.equals(user.getIsBanned())) {
            if (user.getBannedUntil() == null || user.getBannedUntil().isAfter(LocalDateTime.now())) {
                throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "Account is banned");
            }
        }

        // Generate tokens
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getUsername(),
                user.getRole()
        );
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        // Set cookies
        setAuthCookie(response, accessToken);
        setRefreshTokenCookie(response, refreshToken);

        // Update last login time
        userService.updateLastLoginAt(user.getId());

        // Build response
        String csrfToken = IdUtil.simpleUUID();
        UserVO userVO = userService.toVO(user);

        return LoginResponse.builder()
                .csrfToken(csrfToken)
                .user(userVO)
                .build();
    }

    @Override
    @Transactional
    public LoginResponse register(RegisterDTO registerDTO, HttpServletResponse response) {
        // Check if username already exists
        Long usernameCount = userMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getUsername, registerDTO.getUsername())
        );
        if (usernameCount > 0) {
            throw new BusinessException(ErrorCode.AUTH_USERNAME_TAKEN);
        }

        // Check if email already exists (if provided)
        if (registerDTO.getEmail() != null && !registerDTO.getEmail().isBlank()) {
            Long emailCount = userMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                            .eq(User::getEmail, registerDTO.getEmail())
            );
            if (emailCount > 0) {
                throw new BusinessException(ErrorCode.AUTH_EMAIL_TAKEN);
            }
        }

        // Create new user
        User user = new User();
        user.setId(IdUtil.fastSimpleUUID());
        user.setUsername(registerDTO.getUsername());
        user.setName(registerDTO.getUsername());
        user.setEmail(registerDTO.getEmail());
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setRole("USER");
        user.setIsActive(true);
        user.setIsBanned(false);
        user.setJoinedAt(LocalDateTime.now());

        userMapper.insert(user);

        // Log in the new user
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername(registerDTO.getUsername());
        loginDTO.setPassword(registerDTO.getPassword());

        return login(loginDTO, response);
    }

    @Override
    public LoginResponse refresh(String refreshToken, HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED, "Refresh token is required");
        }

        // Validate refresh token
        String userId;
        try {
            userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
            if (userId == null) {
                throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED, "Invalid refresh token");
            }
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED, "Refresh token has expired");
        }

        // Find user
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND);
        }

        // Check if user is still active
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "Account is not active");
        }

        // Check if user is banned
        if (Boolean.TRUE.equals(user.getIsBanned())) {
            if (user.getBannedUntil() == null || user.getBannedUntil().isAfter(LocalDateTime.now())) {
                throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "Account is banned");
            }
        }

        // Generate new tokens
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getUsername(),
                user.getRole()
        );
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        // Set cookies
        setAuthCookie(response, accessToken);
        setRefreshTokenCookie(response, newRefreshToken);

        // Build response
        String csrfToken = IdUtil.simpleUUID();
        UserVO userVO = userService.toVO(user);

        return LoginResponse.builder()
                .csrfToken(csrfToken)
                .user(userVO)
                .build();
    }

    @Override
    public void logout(HttpServletResponse response) {
        clearAuthCookies(response);
    }

    /**
     * Set the authentication cookie with the access token.
     *
     * @param response    the HTTP response
     * @param accessToken the access token
     */
    private void setAuthCookie(HttpServletResponse response, String accessToken) {
        JwtProperties.AccessTokenCookie cookieConfig = jwtProperties.getCookie().getAccessToken();

        // Use setHeader to construct full cookie string including SameSite
        // (Servlet Cookie API doesn't support SameSite directly)
        String sameSite = cookieConfig.getSameSite();
        String headerValue = String.format("%s=%s; Path=%s; Max-Age=%d; HttpOnly%s; SameSite=%s",
                cookieConfig.getName(),
                accessToken,
                cookieConfig.getPath(),
                cookieConfig.getMaxAge(),
                cookieConfig.isSecure() ? "; Secure" : "",
                sameSite
        );
        response.setHeader("Set-Cookie", headerValue);
    }

    /**
     * Set the refresh token cookie.
     *
     * @param response     the HTTP response
     * @param refreshToken the refresh token
     */
    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        JwtProperties.RefreshTokenCookie cookieConfig = jwtProperties.getCookie().getRefreshToken();

        String headerValue = String.format("%s=%s; Path=%s; Max-Age=%d; HttpOnly%s; SameSite=%s",
                cookieConfig.getName(),
                refreshToken,
                cookieConfig.getPath(),
                cookieConfig.getMaxAge(),
                cookieConfig.isSecure() ? "; Secure" : "",
                cookieConfig.getSameSite()
        );
        response.addHeader("Set-Cookie", headerValue);
    }

    /**
     * Clear both authentication cookies.
     *
     * @param response the HTTP response
     */
    private void clearAuthCookies(HttpServletResponse response) {
        JwtProperties.CookieConfig cookieConfig = jwtProperties.getCookie();

        // Clear access_token
        String accessHeader = String.format("%s=; Path=%s; Max-Age=0; HttpOnly%s; SameSite=%s",
                cookieConfig.getAccessToken().getName(),
                cookieConfig.getAccessToken().getPath(),
                cookieConfig.getAccessToken().isSecure() ? "; Secure" : "",
                cookieConfig.getAccessToken().getSameSite()
        );
        response.addHeader("Set-Cookie", accessHeader);

        // Clear refresh_token
        String refreshHeader = String.format("%s=; Path=%s; Max-Age=0; HttpOnly%s; SameSite=%s",
                cookieConfig.getRefreshToken().getName(),
                cookieConfig.getRefreshToken().getPath(),
                cookieConfig.getRefreshToken().isSecure() ? "; Secure" : "",
                cookieConfig.getRefreshToken().getSameSite()
        );
        response.addHeader("Set-Cookie", refreshHeader);
    }
}
