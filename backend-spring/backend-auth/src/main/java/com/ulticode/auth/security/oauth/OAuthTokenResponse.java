package com.ulticode.auth.security.oauth;

/**
 * Normalized token response returned by code exchange.
 */
public record OAuthTokenResponse(
        String accessToken,
        String tokenType,
        String scope
) {}
