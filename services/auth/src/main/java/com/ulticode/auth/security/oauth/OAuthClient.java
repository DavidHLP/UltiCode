package com.ulticode.auth.security.oauth;

/**
 * Port for provider-specific OAuth mechanics in backend-auth.
 */
public interface OAuthClient {

    String getProviderName();

    String buildAuthorizationUrl(String state, String redirectUri);

    OAuthTokenResponse exchangeCodeForToken(String code, String redirectUri);

    OAuthUserInfo fetchUserInfo(String accessToken);
}
