package com.ulticode.modules.auth.session;

import cn.hutool.core.util.IdUtil;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.security.jwt.JwtProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Deep module that owns the OAuth state lifecycle (security invariant #5).
 *
 * <p>Before this module existed, {@code OAuthService} interleaved state
 * issuance and consumption with provider-specific token exchange and user
 * upsert. The state Redis key prefix, TTL, cookie name, cookie {@code Path},
 * and the coupling of the {@code Secure} flag to the access-token cookie
 * config were spread across six private helpers in {@code OAuthService}, and
 * {@code validateOAuthState} was private — unreachable by tests except through
 * the full provider callback (which then failed on the real GitHub HTTP call).
 *
 * <p>State issuance, atomic consumption, cookie wiring, and the Secure-flag
 * coupling are now changed in one place. The interface is the test surface:
 * each branch (blank / unknown / consumed) can be exercised directly with a
 * mapper-style mock, with no HTTP provider in the loop.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuthStateModule implements OAuthStatePort {

    private static final String OAUTH_STATE_PREFIX = "oauth:state:";
    private static final Duration OAUTH_STATE_TTL = Duration.ofMinutes(5);
    private static final String STATE_COOKIE_PATH = "/auth";
    private static final String STATE_COOKIE_SAMESITE = "Lax";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    @Override
    public String issueState(String provider, HttpServletResponse response) {
        String state = IdUtil.simpleUUID();
        redisTemplate.opsForValue().set(stateKey(provider, state), "1", OAUTH_STATE_TTL);
        setStateCookie(provider, state, response);
        return state;
    }

    @Override
    public void validateAndConsume(String provider, String state, HttpServletResponse response) {
        if (state == null || state.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "OAuth state parameter is missing");
        }
        String consumed = redisTemplate.opsForValue().getAndDelete(stateKey(provider, state));
        clearStateCookie(provider, response);
        if (consumed == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid or expired OAuth state parameter");
        }
    }

    private String stateKey(String provider, String state) {
        return OAUTH_STATE_PREFIX + provider + ":" + state;
    }

    private void setStateCookie(String provider, String state, HttpServletResponse response) {
        response.addHeader("Set-Cookie", String.format(
            "%s=%s; Path=%s; Max-Age=%d; HttpOnly%s; SameSite=%s",
            cookieName(provider), state, STATE_COOKIE_PATH, OAUTH_STATE_TTL.toSeconds(),
            isSecure() ? "; Secure" : "", STATE_COOKIE_SAMESITE));
    }

    private void clearStateCookie(String provider, HttpServletResponse response) {
        response.addHeader("Set-Cookie", String.format(
            "%s=; Path=%s; Max-Age=0; HttpOnly%s; SameSite=%s",
            cookieName(provider), STATE_COOKIE_PATH,
            isSecure() ? "; Secure" : "", STATE_COOKIE_SAMESITE));
    }

    private String cookieName(String provider) {
        return "oauth_state_" + provider;
    }

    private boolean isSecure() {
        return jwtProperties.getCookie().getAccessToken().isSecure();
    }
}
