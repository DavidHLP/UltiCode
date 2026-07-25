package com.ulticode.modules.auth.service.oauth;

import cn.hutool.http.HttpRequest;

/**
 * Provider-agnostic execution seam for outbound OAuth HTTP requests.
 *
 * <p>Owns the two security-relevant execution invariants that must not be
 * forgotten when a third provider is added:
 *
 * <ul>
 *   <li><b>Bounded timeouts.</b> Hutool's default timeout is {@code -1},
 *       which leaves the JDK connect/read timeouts at {@code 0} (infinite).
 *       A slow or hostile provider endpoint would then pin a servlet thread
 *       and exhaust the HTTP connection pool.</li>
 *   <li><b>Status gate.</b> A non-2xx provider response (400 invalid_grant,
 *       401 bad client secret, 5xx) is rejected before the body is parsed,
 *       so a provider error body is never mistaken for a token/user
 *       payload. The failure carries only the HTTP status &mdash; never
 *       the body, which may echo the request or secret.</li>
 * </ul>
 *
 * <p>Implementations <b>must</b>:
 * <ul>
 *   <li>close the underlying {@link cn.hutool.http.HttpResponse} on every
 *       path (success and failure) so the connection is released;</li>
 *   <li>apply bounded connect and read timeouts before executing;</li>
 *   <li>reject non-2xx responses without surfacing the response body in the
 *       thrown exception.</li>
 * </ul>
 *
 * <p>The interface exists so tests can swap the production transport for a
 * deterministic in-memory fake without standing up an HTTP server (see
 * {@code FakeOAuthHttpTransport} in the test sources).
 */
public interface OAuthHttpTransport {

    /**
     * Execute the request and return the response body.
     *
     * @param request   the provider-specific request (auth header, form
     *                  body, and any other headers already set by the
     *                  caller); the transport owns timeouts and the status
     *                  gate, the caller owns the wire shape
     * @param provider  provider name, used in the failure message
     * @param operation operation name (e.g. {@code "token exchange"}), used
     *                  in the failure message
     * @return the response body
     * @throws com.ulticode.common.exception.BusinessException
     *         {@link com.ulticode.common.exception.ErrorCode#AUTH_INVALID_CREDENTIALS}
     *         on any non-2xx response or transport failure
     */
    String executeForBody(HttpRequest request, String provider, String operation);
}
