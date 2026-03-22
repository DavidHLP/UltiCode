package com.ulticode.modules.auth.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.refreshtoken.service.RefreshTokenService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Password reset service.
 * Handles forgot password and password reset functionality.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserMapper userMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.frontend-url:http://localhost:9002}")
    private String frontendUrl;

    private static final String RESET_PREFIX = "password-reset:";
    private static final Duration RESET_TTL = Duration.ofHours(1);

    /**
     * Forgot password - send reset email.
     *
     * @param email the user's email address
     */
    @RateLimit(key = "'forgot-password:' + #email", limit = 3, period = 3600)
    public void forgotPassword(String email) {
        User user = userMapper.selectOne(
            new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)
        );

        if (user == null) {
            // Do not reveal whether user exists
            log.debug("Password reset requested for non-existent email: {}", email);
            return;
        }

        String token = IdUtil.simpleUUID();
        String key = RESET_PREFIX + token;

        redisTemplate.opsForValue().set(key, user.getId(), RESET_TTL);

        // TODO: Send email (Phase 3 full implementation)
        String resetUrl = frontendUrl + "/reset-password?token=" + token;
        log.info("Password reset URL for {}: {}", email, resetUrl);
    }

    /**
     * Reset password using token.
     *
     * @param token      the reset token from email
     * @param newPassword the new password
     * @throws BusinessException if token is invalid or expired
     */
    public void resetPassword(String token, String newPassword) {
        String key = RESET_PREFIX + token;
        String userId = redisTemplate.opsForValue().get(key);

        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_RESET_TOKEN, "Invalid or expired reset token");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.AUTH_USER_NOT_FOUND, "User not found");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);

        // Delete reset token
        redisTemplate.delete(key);

        // Revoke all user sessions
        refreshTokenService.revokeAllUserTokens(userId);

        log.info("Password reset completed for user: {}", userId);
    }
}
