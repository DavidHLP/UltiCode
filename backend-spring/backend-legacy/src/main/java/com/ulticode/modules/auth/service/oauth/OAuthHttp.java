package com.ulticode.modules.auth.service.oauth;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * Production {@link OAuthHttpTransport} backed by Hutool. Owns the
 * security-relevant execution policy shared by every provider adapter:
 *
 * <ul>
 *   <li><b>Bounded timeouts</b> &mdash; Hutool's default timeout is
 *       {@code -1}, which leaves the JDK connect/read timeouts at
 *       {@code 0} (infinite). A slow or hostile provider endpoint would
 *       then pin a servlet thread and exhaust the HTTP connection pool.</li>
 *   <li><b>Status gate</b> &mdash; A non-2xx provider response
 *       (400 invalid_grant, 401 bad client secret, 5xx) is rejected before
 *       the body is parsed, so a provider error body is never mistaken
 *       for a token/user payload. The failure carries only the HTTP
 *       status &mdash; never the body, which may echo the request or
 *       secret.</li>
 * </ul>
 *
 * <p>The {@link HttpResponse} is closed via try-with-resources so the
 * underlying connection is released on every path.
 *
 * <p>Registered as a Spring {@code @Component}; provider adapters inject
 * the {@link OAuthHttpTransport} interface, not this implementation, so
 * tests can swap a deterministic fake transport without standing up a
 * real HTTP server.
 */
@Component
public class OAuthHttp implements OAuthHttpTransport {

    /** Bounded connect timeout in ms (Hutool's default leaves the JDK timeout at 0/infinite). */
    static final int CONNECT_TIMEOUT_MS = 5_000;

    /** Bounded read timeout in ms. */
    static final int READ_TIMEOUT_MS = 10_000;

    @Override
    public String executeForBody(HttpRequest request, String provider, String operation) {
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
