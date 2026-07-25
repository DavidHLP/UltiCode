package com.ulticode.modules.auth.service.oauth;

/**
 * Token-exchange response normalized across providers.
 *
 * <p>Provider adapters parse their provider-specific JSON response into
 * this shape so the {@code OAuthService} coordinator never has to know
 * about GitHub vs Google response differences.
 *
 * @param accessToken the access token to use against the user-info endpoint
 */
public record OAuthTokenResponse(String accessToken) {
}
