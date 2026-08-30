package com.ulticode.websecurity.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Redis-backed one-shot claim guard; unavailable Redis fails closed. */
@Component
public final class RedisDelegationAssertionReplayGuard implements DelegationAssertionReplayGuard {

    private static final String KEY_PREFIX = "security:delegation:replay:";

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    public RedisDelegationAssertionReplayGuard(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
    }

    @Override
    public boolean claim(String targetAudience, String jti, Duration ttl) {
        if (targetAudience == null || targetAudience.isBlank()
                || jti == null || jti.isBlank() || ttl == null || ttl.isZero() || ttl.isNegative()) {
            return false;
        }
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(
                    KEY_PREFIX + encode(targetAudience) + ":" + encode(jti), "1", ttl));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
