package com.ulticode.modules.auth.session;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Port for the OAuth state lifecycle — security invariant #5.
 *
 * <p>The deep {@link OAuthStateModule} owns state issuance (Redis bind +
 * HttpOnly cookie) and atomic consumption (Redis getAnd-delete + cookie clear).
 * Callers ({@code OAuthService}) only see this port; tests can swap in an
 * in-memory adapter, or drive the module directly through its mappers, to
 * assert on state issuance/consumption without standing up the full provider
 * token-exchange flow.
 *
 * <p>Enforces security invariant #5 from
 * {@code wiki/concepts/security-invariants.md}: OAuth state is bound to an
 * HttpOnly browser cookie and consumed atomically in Redis, so a stale or
 * replayed state can never complete a callback.
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
     * @param provider the OAuth provider key
     * @param state    the state returned by the provider callback
     * @param response the HTTP response used to clear the state cookie
     * @throws BusinessException with {@link ErrorCode#BAD_REQUEST} if state is
     *                           blank, or {@link ErrorCode#UNAUTHORIZED} if the
     *                           state is unknown, expired, or already consumed
     */
    void validateAndConsume(String provider, String state, HttpServletResponse response);
}
