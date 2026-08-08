package com.ulticode.auth.security.oauth;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.error.AuthErrorCode;
import org.springframework.stereotype.Component;

/**
 * Production implementation of {@link OAuthHttpTransport} for backend-auth.
 */
@Component
public class OAuthHttp implements OAuthHttpTransport {

    static final int CONNECT_TIMEOUT_MS = 5_000;
    static final int READ_TIMEOUT_MS = 10_000;

    @Override
    public String executeForBody(HttpRequest request, String provider, String operation) {
        try (HttpResponse resp = request
                .setConnectionTimeout(CONNECT_TIMEOUT_MS)
                .setReadTimeout(READ_TIMEOUT_MS)
                .execute()) {
            if (!resp.isOk()) {
                throw new AuthBusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS,
                        "OAuth " + provider + " " + operation + " failed: HTTP " + resp.getStatus());
            }
            return resp.body();
        }
    }
}
