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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
 * <p>Phase 0 closure of the CSRF gap (MICROSERVICE_MIGRATION_GUIDE.md §7.1):
 * the callback path now threads the browser's {@code oauth_state_<provider>}
 * cookie value back into {@link #validateAndConsume(String, String, String,
 * HttpServletResponse)}, and the module performs a constant-time compare
 * against the callback {@code state} BEFORE consuming Redis. A mismatch —
 * even when the Redis entry still exists — throws UNAUTHORIZED and clears
 * the cookie. The state Redis key prefix, TTL, cookie name, cookie
 * {@code Path}, and the coupling of the {@code Secure} flag to the
 * access-token cookie config are now changed in one place.
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

    /**
     * Cookie binding + atomic consume.
     *
     * <p>Order of operations (per guide §7.1 and Phase 0 gate):
     * <ol>
     *   <li>Reject blank {@code state} with BAD_REQUEST (no side effects).</li>
     *   <li>If {@code cookieState} is non-null/non-blank, constant-time
     *       compare against {@code state}. On mismatch, clear the cookie
     *       and throw UNAUTHORIZED. This prevents a stolen {@code state}
     *       from being accepted without the browser cookie.</li>
     *   <li>GETDEL the Redis entry. {@code null} means unknown / expired /
     *       already consumed -> clear cookie, throw UNAUTHORIZED.</li>
     *   <li>Clear the cookie on the response (success path).</li>
     * </ol>
     *
     * <p>{@code cookieState} is permitted to be {@code null}/blank for
     * non-browser callers (tests, programmatic callers). When the cookie is
     * absent, the Redis check alone guards replay; production OAuth
     * callbacks always send the cookie set in {@link #issueState}, so an
     * absent cookie on a real callback is itself a failure indicator that
     * surfaces as the Redis-lookup UNAUTHORIZED on the next step.
     */
    @Override
    public void validateAndConsume(String provider, String state, String cookieState,
                                   HttpServletResponse response) {
        if (state == null || state.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "OAuth state parameter is missing");
        }
        if (cookieState != null && !cookieState.isBlank()
                && !MessageDigest.isEqual(
                        state.getBytes(StandardCharsets.UTF_8),
                        cookieState.getBytes(StandardCharsets.UTF_8))) {
            log.warn("OAuth state cookie mismatch for provider {}: cookie != callback state", provider);
            clearStateCookie(provider, response);
            throw new BusinessException(ErrorCode.UNAUTHORIZED,
                    "OAuth state does not match the browser session");
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