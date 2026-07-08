package com.ulticode.modules.auth.service.oauth;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GithubOAuthClient implements OAuthClient {

    private static final String PROVIDER_NAME = "github";

    private final OAuthProperties oauthProperties;
    private final ObjectMapper objectMapper;

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

        String tokenRequestBody = "code=" + code +
            "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);

        String tokenResponse;
        try (HttpResponse resp = HttpRequest.post(github.getTokenUrl())
            .header("Accept", "application/json")
            .header("Authorization", "Basic " + basicAuth)
            .body(tokenRequestBody)
            .execute()) {
            tokenResponse = resp.body();
        }

        try {
            JsonNode tokenNode = objectMapper.readTree(tokenResponse);
            return new OAuthTokenResponse(tokenNode.get("access_token").asText());
        } catch (JsonProcessingException e) {
            log.error("Failed to parse GitHub token response", e);
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "OAuth token exchange failed", e);
        }
    }

    @Override
    public OAuthUserInfo fetchUserInfo(String accessToken) {
        OAuthProperties.OAuthProvider github = oauthProperties.getGithub();

        String userResponse;
        try (HttpResponse resp = HttpRequest.get(github.getUserUrl())
            .header("Authorization", "Bearer " + accessToken)
            .execute()) {
            userResponse = resp.body();
        }

        try {
            JsonNode userNode = objectMapper.readTree(userResponse);
            String githubId = userNode.get("id").asText();
            String name = userNode.has("name") && !userNode.get("name").isNull()
                    ? userNode.get("name").asText()
                    : userNode.get("login").asText();
            String email = userNode.has("email") && !userNode.get("email").isNull()
                    ? userNode.get("email").asText() : null;
            String avatar = userNode.has("avatar_url") ? userNode.get("avatar_url").asText() : null;

            return new OAuthUserInfo(githubId, name, email, avatar);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse GitHub user response", e);
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "Failed to get GitHub user info", e);
        }
    }
}
