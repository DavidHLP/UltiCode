package com.ulticode.auth.security.oauth;

import cn.hutool.core.util.IdUtil;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.error.AuthErrorCode;
import com.ulticode.auth.security.jwt.JwtProperties;
import com.ulticode.common.error.BaseErrorCode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
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
    public String issueState(String provider, HttpServletResponse response) {
        String state = IdUtil.fastSimpleUUID();
        String key = stateKey(provider, state);

        StringRedisTemplate redis = requireRedis();
        redis.opsForValue().set(key, state, OAUTH_STATE_TTL);

        setStateCookie(provider, state, response);
        return state;
    }

    @Override
    public void validateAndConsume(String provider, String state, String cookieState, HttpServletResponse response) {
        try {
            if (state == null || state.isBlank()) {
                throw new AuthBusinessException(BaseErrorCode.BAD_REQUEST, "OAuth state is required");
            }

            if (cookieState != null && !cookieState.isBlank()) {
                if (!constantTimeEquals(state, cookieState)) {
                    log.warn("OAuth state cookie mismatch: provider={}, callbackState={}, cookieState={}",
                            provider, state, cookieState);
                    throw new AuthBusinessException(BaseErrorCode.UNAUTHORIZED, "OAuth state cookie mismatch");
                }
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
        } finally {
            clearStateCookie(provider, response);
        }
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

    private void setStateCookie(String provider, String state, HttpServletResponse response) {
        if (response == null) {
            return;
        }
        Cookie cookie = new Cookie(cookieName(provider), state);
        cookie.setHttpOnly(true);
        cookie.setPath(STATE_COOKIE_PATH);
        cookie.setMaxAge((int) OAUTH_STATE_TTL.toSeconds());
        if (isSecure()) {
            cookie.setSecure(true);
        }
        response.addCookie(cookie);
    }

    private void clearStateCookie(String provider, HttpServletResponse response) {
        if (response == null) {
            return;
        }
        Cookie cookie = new Cookie(cookieName(provider), "");
        cookie.setHttpOnly(true);
        cookie.setPath(STATE_COOKIE_PATH);
        cookie.setMaxAge(0);
        if (isSecure()) {
            cookie.setSecure(true);
        }
        response.addCookie(cookie);
    }

    private String cookieName(String provider) {
        return "oauth_state_" + provider;
    }

    private boolean isSecure() {
        return jwtProperties.getCookie() != null
                && jwtProperties.getCookie().getAccessToken() != null
                && jwtProperties.getCookie().getAccessToken().isSecure();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
