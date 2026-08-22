package com.ulticode.auth.security.oauth;

import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.security.jwt.JwtProperties;
import com.ulticode.auth.session.CookieMutation;
import com.ulticode.common.error.BaseErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthStateModuleTest {

    @Mock
    private ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private JwtProperties jwtProperties;
    private OAuthStateModule stateModule;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        JwtProperties.CookieConfig cookieConfig = new JwtProperties.CookieConfig();
        cookieConfig.getAccessToken().setSecure(true);
        jwtProperties.setCookie(cookieConfig);
        stateModule = new OAuthStateModule(redisTemplateProvider, jwtProperties);
        lenient().when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void issueStatePersistsAtomicStateAndReturnsSecureCookieMutation() {
        OAuthStatePort.OAuthStateIssue issue = stateModule.issueState("github");

        assertThat(issue.state()).isNotBlank();
        assertThat(issue.stateCookie())
                .extracting(CookieMutation::name, CookieMutation::value, CookieMutation::maxAgeSeconds,
                        CookieMutation::httpOnly, CookieMutation::secure, CookieMutation::path)
                .containsExactly("oauth_state_github", issue.state(), 300, true, true, "/auth");
        verify(valueOperations).set(
                eq("oauth:state:github:" + issue.state()),
                eq(issue.state()),
                eq(Duration.ofMinutes(5)));
    }

    @Test
    void validateAndConsumeUsesAtomicGetAndDeleteAndReturnsClearMutation() {
        when(valueOperations.getAndDelete("oauth:state:github" + ":state-1")).thenReturn("state-1");

        CookieMutation clear = stateModule.validateAndConsume("github", "state-1", "state-1");

        assertThat(clear)
                .extracting(CookieMutation::name, CookieMutation::value, CookieMutation::maxAgeSeconds,
                        CookieMutation::httpOnly, CookieMutation::secure, CookieMutation::path)
                .containsExactly("oauth_state_github", "", 0, true, true, "/auth");
        verify(valueOperations).getAndDelete("oauth:state:github:state-1");
    }

    @Test
    void mismatchedCookieFailsBeforeRedisConsume() {
        assertThatThrownBy(() -> stateModule.validateAndConsume("github", "state-1", "other-state"))
                .isInstanceOf(AuthBusinessException.class)
                .satisfies(ex -> assertThat(((AuthBusinessException) ex).getErrorCode())
                        .isEqualTo(BaseErrorCode.UNAUTHORIZED));

        verify(valueOperations, never()).getAndDelete("oauth:state:github:state-1");
    }

    @Test
    void nullOrBlankCookieStateFailsBeforeRedisConsume() {
        for (String cookieState : new String[]{null, "  "}) {
            assertThatThrownBy(() -> stateModule.validateAndConsume("github", "state-1", cookieState))
                    .isInstanceOf(AuthBusinessException.class)
                    .satisfies(ex -> assertThat(((AuthBusinessException) ex).getErrorCode())
                            .isEqualTo(BaseErrorCode.UNAUTHORIZED));
        }

        verify(valueOperations, never()).getAndDelete("oauth:state:github:state-1");
    }

    @Test
    void missingRedisStateIsRejected() {
        when(valueOperations.getAndDelete("oauth:state:github:state-1")).thenReturn(null);

        assertThatThrownBy(() -> stateModule.validateAndConsume("github", "state-1", "state-1"))
                .isInstanceOf(AuthBusinessException.class)
                .satisfies(ex -> assertThat(((AuthBusinessException) ex).getErrorCode())
                        .isEqualTo(BaseErrorCode.UNAUTHORIZED));
    }

    @Test
    void missingStateIsRejectedWithoutRedisCall() {
        assertThatThrownBy(() -> stateModule.validateAndConsume("github", "", null))
                .isInstanceOf(AuthBusinessException.class)
                .satisfies(ex -> assertThat(((AuthBusinessException) ex).getErrorCode())
                        .isEqualTo(BaseErrorCode.BAD_REQUEST));

        verify(valueOperations, never()).getAndDelete("oauth:state:github:");
    }

    @Test
    void redisUnavailableFailsClosed() {
        when(redisTemplateProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> stateModule.issueState("github"))
                .isInstanceOf(AuthBusinessException.class)
                .satisfies(ex -> assertThat(((AuthBusinessException) ex).getErrorCode())
                        .isEqualTo(BaseErrorCode.UNKNOWN_ERROR));
    }

    @Test
    void clearStateCookieRespectsInsecureDevelopmentCookieFlag() {
        jwtProperties.getCookie().getAccessToken().setSecure(false);

        CookieMutation clear = stateModule.clearStateCookie("google");

        assertThat(clear.secure()).isFalse();
        assertThat(clear.path()).isEqualTo("/auth");
        assertThat(clear.maxAgeSeconds()).isZero();
    }
}
