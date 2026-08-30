package com.ulticode.websecurity.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisDelegationAssertionReplayGuardTest {

    @Test
    void unavailableRedisFailsClosed() {
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        assertThat(new RedisDelegationAssertionReplayGuard(provider)
                .claim("backend-app", "jti-1", Duration.ofSeconds(30))).isFalse();
    }

    @Test
    void redisClaimIsAtomicAndTargetScoped() {
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(provider.getIfAvailable()).thenReturn(redis);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("1"), org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(30))))
                .thenReturn(true, false);

        RedisDelegationAssertionReplayGuard guard = new RedisDelegationAssertionReplayGuard(provider);
        assertThat(guard.claim("backend-app", "jti-1", Duration.ofSeconds(30))).isTrue();
        assertThat(guard.claim("backend-app", "jti-1", Duration.ofSeconds(30))).isFalse();
    }

    @Test
    void malformedClaimsFailClosed() {
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        RedisDelegationAssertionReplayGuard guard = new RedisDelegationAssertionReplayGuard(provider);

        assertThat(guard.claim("", "jti-1", Duration.ofSeconds(30))).isFalse();
        assertThat(guard.claim("backend-app", "", Duration.ofSeconds(30))).isFalse();
        assertThat(guard.claim("backend-app", "jti-1", Duration.ZERO)).isFalse();
    }
}
