package com.ulticode.auth.security.oauth;

import cn.hutool.http.HttpRequest;

/**
 * Provider-agnostic execution seam for outbound OAuth HTTP requests in backend-auth.
 */
public interface OAuthHttpTransport {

    /**
     * Execute the request and return the response body.
     */
    String executeForBody(HttpRequest request, String provider, String operation);
}
