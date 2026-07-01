package com.ulticode.modules.auth.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.auth.dto.LoginDTO;
import com.ulticode.modules.auth.dto.LoginResponse;
import com.ulticode.modules.auth.dto.RegisterDTO;
import com.ulticode.modules.auth.service.AuthService;
import com.ulticode.modules.auth.session.AuthSessionPort;

import com.ulticode.modules.refreshtoken.service.RefreshTokenService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import com.ulticode.modules.user.service.UserService;
import com.ulticode.security.jwt.JwtTokenProvider;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Implementation of AuthService.
 *
 * <p>Owns the credential / OAuth-state / refresh-rotation logic.
 * The post-auth tail (cookies, CSRF, JWT, LoginResponse) lives in the deep
 * {@link AuthSessionPort} so a single change there covers login, register,
 * refresh, and OAuth callback paths.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuthSessionPort authSessionPort;

    @Override
    public LoginResponse login(LoginDTO loginDTO, HttpServletResponse response) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, loginDTO.getUsername())
        );

        if (user == null) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        ensureAccountActive(user);

        userService.updateLastLoginAt(user.getId());

        return authSessionPort.completeLogin(user, response);
    }

    @Override
    @Transactional
    public LoginResponse register(RegisterDTO registerDTO, HttpServletResponse response) {
        Long usernameCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, registerDTO.getUsername())
        );
        if (usernameCount > 0) {
            throw new BusinessException(ErrorCode.AUTH_USERNAME_TAKEN);
        }

        if (registerDTO.getEmail() != null && !registerDTO.getEmail().isBlank()) {
            Long emailCount = userMapper.selectCount(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getEmail, registerDTO.getEmail())
            );
            if (emailCount > 0) {
                throw new BusinessException(ErrorCode.AUTH_EMAIL_TAKEN);
            }
        }

        User user = new User();
        user.setId(IdUtil.fastSimpleUUID());
        user.setUsername(registerDTO.getUsername());
        user.setEmail(registerDTO.getEmail());
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setRole("USER");
        user.setIsActive(true);
        user.setIsBanned(false);
        user.setJoinedAt(LocalDateTime.now());
        userMapper.insert(user);
        log.info("Registered new user: {}", user.getUsername());

        userService.updateLastLoginAt(user.getId());

        return authSessionPort.completeLogin(user, response);
    }

    @Override
    public LoginResponse refresh(String refreshToken, HttpServletResponse response) {
        RefreshTokenService.RotationResult rotation;
        try {
            rotation = refreshTokenService.validateAndRotate(refreshToken);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "Refresh token has expired");
        }

        User user = userMapper.selectById(rotation.userId());
        if (user == null) {
            throw new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND);
        }

        ensureAccountActive(user);

        return authSessionPort.completeRefresh(user, rotation.token(), response);
    }

    @Override
    public void logout(String refreshToken, HttpServletResponse response) {
        refreshTokenService.revokePresentedToken(refreshToken);
        authSessionPort.clearSession(response);
    }

    /**
     * Guard that catches disabled / banned accounts and throws the same generic
     * AUTH_INVALID_CREDENTIALS error to avoid leaking account state to the
     * caller. Shared by login and refresh so they cannot drift.
     */
    private void ensureAccountActive(User user) {
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            log.warn("Authentication on inactive account: {}", user.getUsername());
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        if (Boolean.TRUE.equals(user.getIsBanned())) {
            if (user.getBannedUntil() == null || user.getBannedUntil().isAfter(LocalDateTime.now())) {
                log.warn("Authentication on banned account: {}", user.getUsername());
                throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
            }
        }
    }
}
