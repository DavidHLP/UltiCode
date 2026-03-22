package com.ulticode.security.csrf;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

/**
 * CSRF Token 服务
 * 使用 Redis 存储 token, 支持多实例部署
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
        String tokenId = IdUtil.simpleUUID();
        String token = IdUtil.simpleUUID();
        String key = buildKey(userId, tokenId);

        redisTemplate.opsForValue().set(key, token, TOKEN_TTL);

        log.debug("Generated CSRF token for user: {}, tokenId: {}", userId, tokenId);
        return tokenId + ":" + token;
    }

    /**
     * 验证 CSRF Token
     *
     * @param userId 用户ID
     * @param token 客户端提交的 token (格式: tokenId:tokenValue)
     * @return 是否验证通过
     */
    public boolean validateToken(String userId, String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }

        String[] parts = token.split(":");
        if (parts.length != 2) {
            return false;
        }

        String tokenId = parts[0];
        String tokenValue = parts[1];
        String key = buildKey(userId, tokenId);

        String storedValue = redisTemplate.opsForValue().get(key);

        if (storedValue == null || !storedValue.equals(tokenValue)) {
            log.warn("CSRF token validation failed for user: {}, tokenId: {}", userId, tokenId);
            return false;
        }

        // 验证通过后删除 token (一次性使用)
        redisTemplate.delete(key);

        log.debug("CSRF token validated and consumed for user: {}", userId);
        return true;
    }


    /**
     * 清理用户的所有 CSRF tokens (登出时调用)
     *
     * @param userId 用户ID
     */
    public void clearUserTokens(String userId) {
        String pattern = CSRF_PREFIX + userId + ":*";
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys.toArray(new String[0]));
            log.debug("Cleared {} CSRF tokens for user: {}", keys.size(), userId);
        }
    }

    private String buildKey(String userId, String tokenId) {
        return CSRF_PREFIX + userId + ":" + tokenId;
    }
}
