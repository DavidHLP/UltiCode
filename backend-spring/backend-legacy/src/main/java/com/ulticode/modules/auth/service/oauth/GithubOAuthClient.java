package com.ulticode.modules.auth.service.oauth;

import cn.hutool.http.HttpRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.security.oauth.OAuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * GitHub adapter for {@link OAuthClient}.
 *
 * <p>Absorbs every GitHub-specific concern that used to live inline in
 * {@code OAuthService}: the {@code /login/oauth/authorize} query-param
 * shape, the RFC 6749 Basic-Auth header on the token endpoint, the JSON
 * field names on the user endpoint ({@code id / login / name / email /
 * avatar_url}), and the "fall back to {@code login} when {@code name}
 * is null" rule.
 *
 * <p>The state-cookie + Redis atomic-consume lifecycle stays in
 * {@code OAuthService}; this adapter only handles the HTTP-level
 * provider mechanics.
 *
 * <p>Provider-specific request shape (URL, headers, body) is constructed
 * here and delegated to the injected {@link OAuthHttpTransport}, which
 * owns the cross-provider execution policy (bounded timeouts, 2xx status
 * gate, body extraction). Swapping the transport for a deterministic
 * fake in tests exercises this adapter without standing up a real HTTP
 * server.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GithubOAuthClient implements OAuthClient {

    private static final String PROVIDER_NAME = "github";

    private final OAuthProperties oauthProperties;
    private final ObjectMapper objectMapper;
    private final OAuthHttpTransport transport;

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public String buildAuthorizationUrl(String state, String redirectUri) {
        OAuthProperties.OAuthProvider github = oauthProperties.getGithub();
        return UriComponentsBuilder.fromUriString(github.getAuthorizeUrl())
            .queryParam("client_id", github.getClientId())
            .queryParam("redirect_uri", redirectUri)
            .queryParam("scope", github.getScopes())
            .queryParam("state", state)
            .toUriString();
    }

    @Override
    public OAuthTokenResponse exchangeCodeForToken(String code, String redirectUri) {
        OAuthProperties.OAuthProvider github = oauthProperties.getGithub();

        // RFC 6749 Basic Auth instead of plaintext body
        String basicAuth = Base64.getEncoder().encodeToString(
            (github.getClientId() + ":" + github.getClientSecret()).getBytes(StandardCharsets.UTF_8));

        // `code` arrives from the OAuth callback query string, so it is untrusted
        // and must be form-encoded like redirect_uri — otherwise a crafted code
        // can inject extra form parameters into the token request.
        String tokenRequestBody = "code=" + URLEncoder.encode(code, StandardCharsets.UTF_8) +
            "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);

        String tokenResponse = transport.executeForBody(HttpRequest.post(github.getTokenUrl())
            .header("Accept", "application/json")
            .header("Authorization", "Basic " + basicAuth)
            .body(tokenRequestBody), PROVIDER_NAME, "token exchange");

        try {
            JsonNode tokenNode = objectMapper.readTree(tokenResponse);
            String accessToken = tokenNode.path("access_token").asText(null);
            if (accessToken == null || accessToken.isBlank()) {
                throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS,
                    "GitHub token exchange did not return access_token");
            }
            return new OAuthTokenResponse(accessToken);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse GitHub token response", e);
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "OAuth token exchange failed", e);
        }
    }

    @Override
    public OAuthUserInfo fetchUserInfo(String accessToken) {
        OAuthProperties.OAuthProvider github = oauthProperties.getGithub();

        String userResponse = transport.executeForBody(HttpRequest.get(github.getUserUrl())
            .header("Authorization", "Bearer " + accessToken), PROVIDER_NAME, "user info");

        try {
            JsonNode userNode = objectMapper.readTree(userResponse);
            String githubId = userNode.path("id").asText(null);
            if (githubId == null || githubId.isBlank()) {
                throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS,
                    "GitHub user info did not return id");
            }
            String name = userNode.has("name") && !userNode.get("name").isNull()
                    ? userNode.get("name").asText()
                    : userNode.path("login").asText();
            // Phase 0 / MICROSERVICE_MIGRATION_GUIDE.md §7.1: GitHub's
            // /user endpoint exposes `email` only when the user has set
            // it as PUBLIC AND verified it. A null/blank email means we
            // cannot verify ownership. The verified-email auto-link guard
            // lives in OAuthService.createOrUpdateUser, not here.
            String email = userNode.has("email") && !userNode.get("email").isNull()
                    ? userNode.get("email").asText() : null;
            String avatar = userNode.has("avatar_url") ? userNode.get("avatar_url").asText() : null;
            // GitHub public email implies verification; null/blank means unverified.
            boolean emailVerified = (email != null && !email.isBlank());

            return new OAuthUserInfo(githubId, name, email, avatar, emailVerified);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse GitHub user response", e);
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "Failed to get GitHub user info", e);
        }
    }
}
