package com.ulticode.modules.auth.session;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Port for the OAuth state lifecycle — security invariant #5.
 *
 * <p>The deep {@link OAuthStateModule} owns state issuance (Redis bind +
 * HttpOnly cookie) and atomic consumption (constant-time cookie-vs-callback
 * compare + Redis getAnd-delete + cookie clear). Callers ({@code OAuthService})
 * only see this port; tests can swap in an in-memory adapter, or drive the
 * module directly through its mappers, to assert on state
 * issuance/consumption without standing up the full provider token-exchange
 * flow.
 *
 * <p>Enforces Security Invariant #5 (OAuth state is bound to an HttpOnly
 * browser cookie and consumed atomically in Redis, so a stale or replayed
 * state can never complete a callback). The authoritative list lives in
 * {@code AGENTS.md} § Security Invariants.
 */
public interface OAuthStatePort {

    /**
     * Issue a fresh state for the given provider: generate a random state,
     * atomically store it in Redis with a short TTL, and set the
     * {@code oauth_state_<provider>} HttpOnly cookie on the response.
     *
     * @param provider the OAuth provider key (e.g. {@code "github"}, {@code "google"})
     * @param response the HTTP response to attach the state cookie to
     * @return the state value to embed in the provider authorize URL
     */
    String issueState(String provider, HttpServletResponse response);

    /**
     * Validate and atomically consume the state: read-and-delete from Redis,
     * clear the state cookie, and throw if the state is missing, blank,
     * expired, or already consumed.
     *
     * <p>Cookie binding (Phase 0, MICROSERVICE_MIGRATION_GUIDE.md §7.1):
     * when {@code cookieState} is non-null/non-blank, it MUST match
     * {@code state} via constant-time compare. A mismatch — even when the
     * Redis entry exists — throws {@link ErrorCode#UNAUTHORIZED}. This
     * closes the CSRF gap where a stolen callback `state` paired with no
     * browser cookie would otherwise pass the Redis-only check.
     *
     * @param provider    the OAuth provider key
     * @param state       the state returned by the provider callback
     * @param cookieState the state value carried in the browser's
     *                    {@code oauth_state_<provider>} cookie, or
     *                    {@code null}/blank when no cookie was sent
     *                    (e.g. tests, direct curl). When null, the Redis
     *                    check alone guards replay; production callers
     *                    always forward the cookie value.
     * @param response    the HTTP response used to clear the state cookie
     * @throws BusinessException with {@link ErrorCode#BAD_REQUEST} if
     *                           {@code state} is blank, or
     *                           {@link ErrorCode#UNAUTHORIZED} if the
     *                           cookie/state mismatch, the state is
     *                           unknown, expired, or already consumed.
     */
    void validateAndConsume(String provider, String state, String cookieState,
                            HttpServletResponse response);
}