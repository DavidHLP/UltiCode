package com.ulticode.modules.auth.service.oauth;

/**
 * Port for provider-specific OAuth mechanics: authorization URL construction,
 * authorization-code exchange, and user-info fetch.
 *
 * <p>The deep {@code OAuthService} coordinator picks the matching adapter by
 * {@link #getProviderName()}, which must match the key the
 * {@code OAuthStatePort} uses to bind the state-cookie/Redis entry
 * (currently {@code "github"} or {@code "google"}). Provider-specific
 * concerns — endpoint URLs, Basic-Auth header construction, token- and
 * user-info response shapes — live behind this seam so a new provider only
 * adds a single bean, not a new branch in the coordinator.
 *
 * <p>The state-cookie + Redis atomic-consume lifecycle stays in
 * {@code OAuthService}; the port only handles the HTTP-level provider
 * mechanics.
 *
 * <p>Adapters are registered as {@code @Component} beans — Spring autowires
 * the full {@code List<OAuthClient>} into the coordinator. The dispatch is
 * by exact string match on {@link #getProviderName()}, so every adapter
 * MUST return a stable, lower-case key.
 */
public interface OAuthClient {

    /**
     * Provider key used to look up this adapter and to bind the state
     * cookie + Redis entry. Must be stable across releases.
     *
     * @return the provider key (e.g. {@code "github"}, {@code "google"})
     */
    String getProviderName();

    /**
     * Build the provider's {@code /authorize} URL with the given state and
     * redirect URI. The state has already been issued (and bound to the
     * HttpOnly cookie + Redis) by {@code OAuthStatePort} before this call.
     *
     * @param state       the OAuth state to embed in the URL
     * @param redirectUri the redirect URI registered for this client
     * @return the fully-built authorization URL
     */
    String buildAuthorizationUrl(String state, String redirectUri);

    /**
     * Exchange an authorization code for an access token. The Basic-Auth
     * header / form-body construction is provider-specific and lives in the
     * adapter; the coordinator only sees the typed result.
     *
     * @param code        the authorization code from the provider callback
     * @param redirectUri the same redirect URI used at authorize time
     * @return the parsed token response
     */
    OAuthTokenResponse exchangeCodeForToken(String code, String redirectUri);

    /**
     * Fetch the provider's user-info for the given access token. The
     * response shape is provider-specific; the adapter is responsible for
     * normalizing it to {@link OAuthUserInfo}.
     *
     * @param accessToken the access token returned by
     *                    {@link #exchangeCodeForToken(String, String)}
     * @return the normalized user info
     */
    OAuthUserInfo fetchUserInfo(String accessToken);
}
