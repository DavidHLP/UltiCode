package com.ulticode.modules.auth.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.security.jwt.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

/**
 * Unit tests for {@link OAuthStateModule}. The interface is the test surface —
 * each branch of the state lifecycle (issuance, blank rejection, unknown
 * rejection, atomic consumption) is exercised directly with mapper-style
 * mocks, with no HTTP provider and no Spring context in the loop.
 *
 * <p>Replaces the pre-extraction {@code OAuthServiceTest}, which could only
 * reach {@code validateOAuthState} through the full GitHub callback (and then
 * tripped over the real token-exchange HTTP call).
 */
@ExtendWith(MockitoExtension.class)
class OAuthStateModuleTest {

    private static final String OAUTH_STATE_PREFIX = "oauth:state:";
    private static final Duration OAUTH_STATE_TTL = Duration.ofMinutes(5);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> valueOperations;

    private OAuthStateModule module;

    @BeforeEach
    void setUp() {
        module = new OAuthStateModule(redisTemplate, new JwtProperties());
    }

    // ==================== issueState ====================

    @Test
    void issueState_bindsRedisWithFiveMinuteTtlAndReturnsNonBlankState() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String state = module.issueState("github", new MockHttpServletResponse());

        assertThat(state).isNotBlank();
        verify(valueOperations).set(eq(OAUTH_STATE_PREFIX + "github:" + state), eq("1"), eq(OAUTH_STATE_TTL));
    }

    @Test
    void issueState_setsHttpOnlyStateCookieScopedToAuthPath() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        MockHttpServletResponse response = new MockHttpServletResponse();
        String state = module.issueState("google", response);

        String cookie = response.getHeader("Set-Cookie");
        assertThat(cookie).contains("oauth_state_google=" + state)
                .contains("Path=/auth")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
    }

    // ==================== validateAndConsume — blank / null ====================

    @Test
    void validateAndConsume_nullState_throwsBadRequest() {
        assertThatThrownBy(() -> module.validateAndConsume("github", null, new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void validateAndConsume_blankState_throwsBadRequest() {
        assertThatThrownBy(() -> module.validateAndConsume("github", "  ", new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.BAD_REQUEST));
    }

    // ==================== validateAndConsume — unknown / replayed ====================

    @Test
    void validateAndConsume_unknownState_throwsUnauthorized() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(OAUTH_STATE_PREFIX + "github:unknown")).thenReturn(null);

        assertThatThrownBy(() -> module.validateAndConsume("github", "unknown", new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void validateAndConsume_unknownState_stillClearsCookie() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(anyString())).thenReturn(null);

        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            module.validateAndConsume("github", "stale", response);
        } catch (BusinessException ignored) {
            // expected — state is unknown/expired/replayed
        }

        String cookie = response.getHeader("Set-Cookie");
        assertThat(cookie).contains("oauth_state_github=")
                .contains("Max-Age=0");
    }

    // ==================== validateAndConsume — happy path ====================

    @Test
    void validateAndConsume_knownState_consumesAtomicallyAndClearsCookie() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(OAUTH_STATE_PREFIX + "github:known")).thenReturn("1");

        MockHttpServletResponse response = new MockHttpServletResponse();
        module.validateAndConsume("github", "known", response);

        verify(valueOperations).getAndDelete(OAUTH_STATE_PREFIX + "github:known");
        assertThat(response.getHeader("Set-Cookie")).contains("oauth_state_github=")
                .contains("Max-Age=0");
    }
}
