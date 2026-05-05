package com.ulticode.security.csrf;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

import org.springframework.data.redis.core.Cursor;

/**
 * CSRF Token 服务
 * 使用 Redis 存储 token, 支持多实例部署
 *
 * 安全模型：
 * - Token 有效期 24 小时
 * - Token 在会话期间可重复使用（更好的开发体验）
 * - 登出时统一清除所有 Token
 * - 每个 Token 绑定到特定用户
 *
 * 注意：生产环境建议考虑 Token 轮换或更严格的策略
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CsrfService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String CSRF_PREFIX = "csrf:";
    private static final Duration TOKEN_TTL = Duration.ofHours(24);

    /**
     * 生成 CSRF Token 并存储到 Redis
     *
     * @param userId 用户ID
     * @return CSRF token
     */
    public String generateToken(String userId) {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("userId cannot be null or empty");
        }

        String tokenId = IdUtil.simpleUUID();
        String token = IdUtil.simpleUUID();
        String key = buildKey(userId, tokenId);

        redisTemplate.opsForValue().set(key, token, TOKEN_TTL);

        log.debug("Generated CSRF token for user: {}, tokenId: {}", userId, tokenId);
        return tokenId + ":" + token;
    }

    /**
     * 验证 CSRF Token 并轮换（使用后失效，生成新 token）
     *
     * @param userId 用户ID
     * @param token 客户端提交的 token (格式: tokenId:tokenValue)
     * @return 新生成的 CSRF token（客户端需更新存储）
     */
    public String validateAndRotateToken(String userId, String token) {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("userId cannot be null or empty");
        }

        if (token == null || token.isEmpty()) {
            log.debug("CSRF token is null or empty for user: {}", userId);
            return null;
        }

        String[] parts = token.split(":");
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            log.warn("Invalid CSRF token format for user: {}", userId);
            return null;
        }

        String tokenId = parts[0];
        String tokenValue = parts[1];
        String key = buildKey(userId, tokenId);

        String storedValue = redisTemplate.opsForValue().get(key);

        if (storedValue == null || !storedValue.equals(tokenValue)) {
            log.warn("CSRF token validation failed for user: {}, tokenId: {}", userId, tokenId);
            return null;
        }

        // Token rotation: set old token with 5-min grace period TTL instead of immediate delete
        redisTemplate.opsForValue().set(key, storedValue, Duration.ofMinutes(5));
        log.debug("CSRF token validated and rotated for user: {}", userId);
        return generateToken(userId);
    }


    /**
     * 清理用户的所有 CSRF tokens (登出时调用)
     *
     * @param userId 用户ID
     */
    public void clearUserTokens(String userId) {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("userId cannot be null or empty");
        }

        String pattern = CSRF_PREFIX + userId + ":*";
        try (Cursor<String> keys = redisTemplate.scan(
            org.springframework.data.redis.core.ScanOptions.scanOptions().match(pattern).count(100).build()
        )) {
            int count = 0;
            while (keys.hasNext()) {
                redisTemplate.delete(keys.next());
                count++;
            }
            log.debug("Cleared {} CSRF tokens for user: {}", count, userId);
        }
    }

    private String buildKey(String userId, String tokenId) {
        return CSRF_PREFIX + userId + ":" + tokenId;
    }
}
