package com.ulticode.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Prevents the Auth Redisson client from silently downgrading managed Redis TLS. */
@Component
public class AuthRedisTlsGuard {

    public AuthRedisTlsGuard(
            @Value("${AUTH_REDIS_SSL_ENABLED:false}") boolean sslEnabled,
            @Value("${AUTH_REDIS_URL:}") String redisUrl) {
        if (sslEnabled && (redisUrl == null
                || !redisUrl.trim().toLowerCase(java.util.Locale.ROOT).startsWith("rediss://"))) {
            throw new IllegalStateException(
                    "AUTH_REDIS_URL must use rediss:// when AUTH_REDIS_SSL_ENABLED=true");
        }
    }
}
