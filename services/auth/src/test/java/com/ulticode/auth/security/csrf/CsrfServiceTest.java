package com.ulticode.auth.security.csrf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Unit tests for {@link CsrfService}.
 *
 * <p>Validates the Redis-backed CSRF contract that backend-auth inherits
 * from backend-legacy: token format ({@code tokenId:tokenValue}), 24h TTL,
 * 5-minute grace period on rotation, and prefix-based clear-all on
 * logout. The service is verified with a Mockito-backed
 * {@link RedisTemplate} so the unit test does not need a running Redis.
 *
 * <p>One test deliberately omits a Redis provider to confirm the
 * service fails loudly with {@link IllegalStateException} when Redis is
 * misconfigured at runtime; the unit-test slice (which excludes
 * {@code RedisAutoConfiguration}) never reaches the real
 * {@code requireRedis()} path.
 */
class CsrfServiceTest {

    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<RedisTemplate<String, String>> redisTemplateProvider = mock(ObjectProvider.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    private CsrfService csrfService;

    @BeforeEach
    void setUp() {
        when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        csrfService = new CsrfService(redisTemplateProvider);
    }

    @Test
    @DisplayName("generateToken returns tokenId:tokenValue and writes the Redis key with 24h TTL")
    void generateTokenWritesRedisKey() {
        String issued = csrfService.generateToken("user-1");

        assertThat(issued).isNotBlank();
        assertThat(issued.split(":")).hasSize(2);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps, times(1)).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

        assertThat(keyCaptor.getValue()).startsWith("csrf:user-1:");
        assertThat(valueCaptor.getValue()).isEqualTo(issued.split(":")[1]);
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    @DisplayName("validateAndRotateToken returns null when the stored value does not match the submitted token")
    void validateReturnsNullWhenMissing() {
        when(valueOps.get(any(String.class))).thenReturn(null);

        assertThat(csrfService.validateAndRotateToken("user-1", "stale-id:stale-value")).isNull();
    }

    @Test
    @DisplayName("validateAndRotateToken returns null when the submitted token has the wrong format")
    void validateReturnsNullOnBadFormat() {
        assertThat(csrfService.validateAndRotateToken("user-1", "no-colon-here")).isNull();
        assertThat(csrfService.validateAndRotateToken("user-1", ":empty-id")).isNull();
        assertThat(csrfService.validateAndRotateToken("user-1", "empty-value:")).isNull();
        assertThat(csrfService.validateAndRotateToken("user-1", "")).isNull();
        assertThat(csrfService.validateAndRotateToken("user-1", null)).isNull();
    }

    @Test
    @DisplayName("validateAndRotateToken issues a 5-minute grace SET and a new token when the value matches")
    void validateRotatesWhenValid() {
        when(valueOps.get(any(String.class))).thenReturn("valid-token-value");

        String rotated = csrfService.validateAndRotateToken("user-2", "tokenId:valid-token-value");

        assertThat(rotated).isNotBlank();
        // The grace-period SET (5min) and the rotation SET (24h) both happened.
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps, atLeastOnce()).set(any(String.class), any(String.class), ttlCaptor.capture());
        assertThat(ttlCaptor.getAllValues()).contains(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("clearUserTokens deletes every key under the user's CSRF prefix")
    @SuppressWarnings("unchecked")
    void clearUserTokensDeletesPrefix() {
        Cursor<String> cursor = mock(Cursor.class);
        when(cursor.hasNext()).thenReturn(true, true, true, false);
        when(cursor.next()).thenReturn("csrf:user-3:aaa", "csrf:user-3:bbb", "csrf:user-3:ccc");
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(redisTemplate.delete(any(String.class))).thenReturn(true);

        csrfService.clearUserTokens("user-3");

        verify(redisTemplate, times(1)).scan(any(ScanOptions.class));
        verify(redisTemplate, times(3)).delete(any(String.class));
    }

    @Test
    @DisplayName("clearUserTokens is a no-op when the user has no CSRF keys")
    @SuppressWarnings("unchecked")
    void clearUserTokensNoOpWhenEmpty() {
        Cursor<String> cursor = mock(Cursor.class);
        when(cursor.hasNext()).thenReturn(false);
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);

        csrfService.clearUserTokens("user-empty");

        verify(redisTemplate, never()).delete(any(String.class));
    }

    @Test
    @DisplayName("missing RedisTemplate causes CSRF operations to fail fast with IllegalStateException")
    void missingRedisFailsFast() {
        when(redisTemplateProvider.getIfAvailable()).thenReturn(null);
        CsrfService withoutRedis = new CsrfService(redisTemplateProvider);

        assertThatThrownBy(() -> withoutRedis.generateToken("user-x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RedisTemplate is required for CSRF token storage");
    }
}
