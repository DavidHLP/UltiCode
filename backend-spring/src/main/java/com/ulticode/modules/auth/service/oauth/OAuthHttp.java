package com.ulticode.modules.auth.service.oauth;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;

/**
 * Shared OAuth provider HTTP execution policy.
 *
 * <p>Provider adapters build the provider-specific {@link HttpRequest} (Basic-Auth
 * header, form body, field mapping); this helper owns the execute policy so the
 * two security-relevant invariants cannot be forgotten when a third provider is
 * added:
 *
 * <ul>
 *   <li><b>Bounded timeouts.</b> Hutool's default timeout is {@code -1}, which
 *       leaves the JDK connect/read timeouts at {@code 0} (infinite). A slow or
 *       hostile provider endpoint would then pin a servlet thread and exhaust
 *       the HTTP connection pool. The bounded constants below cap both phases.</li>
 *   <li><b>Status gate.</b> A non-2xx provider response (400 invalid_grant, 401
 *       bad client secret, 5xx) is rejected before the body is parsed, so a
 *       provider error body is never mistaken for a token/user payload. The
 *       failure carries only the HTTP status &mdash; never the body, which may
 *       echo the request or secret.</li>
 * </ul>
 *
 * <p>The {@link HttpResponse} is closed via try-with-resources so the underlying
 * connection is released on every path.
 */
final class OAuthHttp {

    /** Bounded connect timeout in ms (Hutool's default leaves the JDK timeout at 0/infinite). */
    static final int CONNECT_TIMEOUT_MS = 5_000;

    /** Bounded read timeout in ms. */
    static final int READ_TIMEOUT_MS = 10_000;

    private OAuthHttp() {
    }

    /**
     * Execute the request with bounded timeouts, require a 2xx response, and
     * return the response body.
     *
     * @param request   the provider-specific request (auth header + body already set)
     * @param provider  provider name, used in the failure message
     * @param operation operation name (e.g. {@code "token exchange"}), used in the failure message
     * @return the response body
     * @throws BusinessException {@link ErrorCode#AUTH_INVALID_CREDENTIALS} on any non-2xx response
     */
    static String executeForBody(HttpRequest request, String provider, String operation) {
        try (HttpResponse resp = request
                .setConnectionTimeout(CONNECT_TIMEOUT_MS)
                .setReadTimeout(READ_TIMEOUT_MS)
                .execute()) {
            if (!resp.isOk()) {
                throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS,
                        "OAuth " + provider + " " + operation + " failed: HTTP " + resp.getStatus());
            }
            return resp.body();
        }
    }
}
