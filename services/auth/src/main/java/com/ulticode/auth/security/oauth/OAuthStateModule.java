package com.ulticode.auth.security.oauth;

import cn.hutool.core.util.IdUtil;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.security.jwt.JwtProperties;
import com.ulticode.auth.session.CookieMutation;
import com.ulticode.common.error.BaseErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

/**
 * Implementation of {@link OAuthStatePort} owning state issuance and atomic consumption in backend-auth.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuthStateModule implements OAuthStatePort {

    private static final String OAUTH_STATE_PREFIX = "oauth:state:";
    private static final Duration OAUTH_STATE_TTL = Duration.ofMinutes(5);
    private static final String STATE_COOKIE_PATH = "/auth";

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final JwtProperties jwtProperties;

    @Override
    public OAuthStateIssue issueState(String provider) {
        String state = IdUtil.fastSimpleUUID();
        String key = stateKey(provider, state);

        StringRedisTemplate redis = requireRedis();
        redis.opsForValue().set(key, state, OAUTH_STATE_TTL);

        return new OAuthStateIssue(state, stateCookie(provider, state));
    }

    @Override
    public CookieMutation validateAndConsume(String provider, String state, String cookieState) {
        if (state == null || state.isBlank()) {
            throw new AuthBusinessException(BaseErrorCode.BAD_REQUEST, "OAuth state is required");
        }

        if (cookieState == null || cookieState.isBlank()) {
            throw new AuthBusinessException(BaseErrorCode.UNAUTHORIZED, "OAuth state cookie is required");
        }
        if (!constantTimeEquals(state, cookieState)) {
            log.warn("OAuth state cookie mismatch: provider={}, callbackState={}, cookieState={}",
                    provider, state, cookieState);
            throw new AuthBusinessException(BaseErrorCode.UNAUTHORIZED, "OAuth state cookie mismatch");
        }

        String key = stateKey(provider, state);
        StringRedisTemplate redis = requireRedis();
        String stored = redis.opsForValue().getAndDelete(key);

        if (stored == null) {
            log.warn("OAuth state missing or expired in Redis: provider={}", provider);
            throw new AuthBusinessException(BaseErrorCode.UNAUTHORIZED, "OAuth state invalid or expired");
        }

        if (!constantTimeEquals(state, stored)) {
            log.warn("OAuth state value mismatch against Redis: provider={}", provider);
            throw new AuthBusinessException(BaseErrorCode.UNAUTHORIZED, "OAuth state invalid");
        }

        return clearStateCookie(provider);
    }

    @Override
    public CookieMutation clearStateCookie(String provider) {
        return stateCookie(provider, "", 0);
    }

    private StringRedisTemplate requireRedis() {
        StringRedisTemplate template = redisTemplateProvider.getIfAvailable();
        if (template == null) {
            throw new AuthBusinessException(BaseErrorCode.UNKNOWN_ERROR, "RedisTemplate unavailable in backend-auth");
        }
        return template;
    }

    private String stateKey(String provider, String state) {
        return OAUTH_STATE_PREFIX + provider + ":" + state;
    }

    private CookieMutation stateCookie(String provider, String state) {
        return stateCookie(provider, state, (int) OAUTH_STATE_TTL.toSeconds());
    }

    private CookieMutation stateCookie(String provider, String state, int maxAgeSeconds) {
        JwtProperties.AccessTokenCookie config = jwtProperties.getCookie().getAccessToken();
        return new CookieMutation(cookieName(provider), state, maxAgeSeconds, true,
                config.isSecure(), config.getSameSite(), STATE_COOKIE_PATH, config.getDomain());
    }

    private String cookieName(String provider) {
        return "oauth_state_" + provider;
    }


    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
