package com.ulticode.security.csrf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CsrfService")
class CsrfServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private CsrfService csrfService;

    private static final String USER_ID = "user-123";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Nested
    @DisplayName("generateToken()")
    class GenerateToken {

        @Test
        @DisplayName("stores token in Redis and returns tokenId:token format")
        void storesInRedis() {
            String token = csrfService.generateToken(USER_ID);

            assertThat(token).isNotBlank();
            assertThat(token).contains(":");
            String[] parts = token.split(":");
            assertThat(parts).hasSize(2);
            assertThat(parts[0]).isNotBlank();
            assertThat(parts[1]).isNotBlank();
            verify(valueOperations).set(anyString(), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("throws for null userId")
        void nullUserId_throws() {
            assertThatThrownBy(() -> csrfService.generateToken(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws for empty userId")
        void emptyUserId_throws() {
            assertThatThrownBy(() -> csrfService.generateToken(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("validateAndRotateToken()")
    class ValidateAndRotateToken {

        @Test
        @DisplayName("returns null for invalid token format (no colon)")
        void invalidFormat_returnsNull() {
            String result = csrfService.validateAndRotateToken(USER_ID, "no-colon-here");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("returns null for empty token")
        void emptyToken_returnsNull() {
            String result = csrfService.validateAndRotateToken(USER_ID, "");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("returns null for null token")
        void nullToken_returnsNull() {
            String result = csrfService.validateAndRotateToken(USER_ID, null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("returns new token on successful validation (rotation)")
        @SuppressWarnings("unchecked")
        void validToken_returnsNewToken() {
            // Generate a token first to get the expected format
            String originalToken = csrfService.generateToken(USER_ID);
            String tokenId = originalToken.split(":")[0];
            String tokenValue = originalToken.split(":")[1];

            // Mock: the stored value matches what was generated
            when(valueOperations.get(anyString())).thenReturn(tokenValue);

            // Validate and rotate
            String newToken = csrfService.validateAndRotateToken(USER_ID, originalToken);

            assertThat(newToken).isNotBlank();
            assertThat(newToken).contains(":");
            // Verify old token was set with 5-min grace period TTL
            verify(valueOperations).set(anyString(), anyString(), eq(Duration.ofMinutes(5)));
        }

        @Test
        @DisplayName("old token has 5-minute TTL instead of immediate delete (grace period)")
        @SuppressWarnings("unchecked")
        void testOldTokenValidWithinGracePeriod() {
            String originalToken = csrfService.generateToken(USER_ID);
            String tokenId = originalToken.split(":")[0];
            String tokenValue = originalToken.split(":")[1];

            when(valueOperations.get(anyString())).thenReturn(tokenValue);

            csrfService.validateAndRotateToken(USER_ID, originalToken);

            // Verify set with 5-minute TTL, NOT delete
            verify(valueOperations).set(anyString(), eq(tokenValue), eq(Duration.ofMinutes(5)));
        }

        @Test
        @DisplayName("returns null when stored value does not match")
        void wrongStoredValue_returnsNull() {
            when(valueOperations.get(anyString())).thenReturn("wrong-value");

            String result = csrfService.validateAndRotateToken(USER_ID, "some-token:some-value");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("old token is NOT immediately deleted - has 5-minute TTL (grace period)")
        @SuppressWarnings("unchecked")
        void testOldTokenExpiredAfterGracePeriod() {
            String originalToken = csrfService.generateToken(USER_ID);
            String tokenId = originalToken.split(":")[0];
            String tokenValue = originalToken.split(":")[1];

            when(valueOperations.get(anyString())).thenReturn(tokenValue);

            csrfService.validateAndRotateToken(USER_ID, originalToken);

            // Verify TTL is set to 5 minutes (not immediate delete)
            verify(valueOperations).set(anyString(), eq(tokenValue), eq(Duration.ofMinutes(5)));
        }

        @Test
        @DisplayName("throws for null userId")
        void nullUserId_throws() {
            assertThatThrownBy(() -> csrfService.validateAndRotateToken(null, "some:token"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("clearUserTokens()")
    class ClearUserTokens {

        @Test
        @DisplayName("scans and deletes all CSRF keys for user")
        @SuppressWarnings("unchecked")
        void callsScanAndDelete() {
            Cursor<String> cursor = mock(Cursor.class);
            when(cursor.hasNext()).thenReturn(true, true, false);
            when(cursor.next()).thenReturn("csrf:user-123:token1", "csrf:user-123:token2");
            when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);

            csrfService.clearUserTokens(USER_ID);

            verify(redisTemplate).scan(any(ScanOptions.class));
            verify(redisTemplate).delete("csrf:user-123:token1");
            verify(redisTemplate).delete("csrf:user-123:token2");
        }

        @Test
        @DisplayName("throws for null userId")
        void nullUserId_throws() {
            assertThatThrownBy(() -> csrfService.clearUserTokens(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws for empty userId")
        void emptyUserId_throws() {
            assertThatThrownBy(() -> csrfService.clearUserTokens(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
