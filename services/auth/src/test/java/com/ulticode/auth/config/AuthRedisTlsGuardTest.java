package com.ulticode.auth.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthRedisTlsGuardTest {

    @Test
    void rejectsTlsFlagWithPlainRedisUrl() {
        assertThatThrownBy(() -> new AuthRedisTlsGuard(true, "redis://redis:6379"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rediss://");
    }

    @Test
    void acceptsManagedTlsUrlAndLocalPlainDefaults() {
        assertThatCode(() -> new AuthRedisTlsGuard(true, "rediss://redis.example.invalid:6380"))
                .doesNotThrowAnyException();
        assertThatCode(() -> new AuthRedisTlsGuard(false, "redis://redis:6379"))
                .doesNotThrowAnyException();
    }
}
