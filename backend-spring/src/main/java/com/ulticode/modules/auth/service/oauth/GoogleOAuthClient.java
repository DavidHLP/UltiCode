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
 * Google adapter for {@link OAuthClient}.
 *
 * <p>Absorbs every Google-specific concern that used to live inline in
 * {@code OAuthService}: the {@code /o/oauth2/v2/auth} query-param shape
 * (including {@code response_type=code}), the RFC 6749 Basic-Auth
 * header on the token endpoint, the {@code grant_type} on the form body,
 * the JSON field names on the user endpoint ({@code id / email / name /
 * picture}), and the "fall back to the local-part of {@code email} when
 * {@code name} is missing" rule.
 *
 * <p>The state-cookie + Redis atomic-consume lifecycle stays in
 * {@code OAuthService}; this adapter only handles the HTTP-level
 * provider mechanics.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleOAuthClient implements OAuthClient {

    private static final String PROVIDER_NAME = "google";

    private final OAuthProperties oauthProperties;
    private final ObjectMapper objectMapper;

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public String buildAuthorizationUrl(String state, String redirectUri) {
        OAuthProperties.OAuthProvider google = oauthProperties.getGoogle();
        return UriComponentsBuilder.fromUriString(google.getAuthorizeUrl())
            .queryParam("client_id", google.getClientId())
            .queryParam("redirect_uri", redirectUri)
            .queryParam("response_type", "code")
            .queryParam("scope", google.getScopes())
            .queryParam("state", state)
            .toUriString();
    }

    @Override
    public OAuthTokenResponse exchangeCodeForToken(String code, String redirectUri) {
        OAuthProperties.OAuthProvider google = oauthProperties.getGoogle();

        // RFC 6749 Basic Auth instead of plaintext body
        String basicAuth = Base64.getEncoder().encodeToString(
            (google.getClientId() + ":" + google.getClientSecret()).getBytes(StandardCharsets.UTF_8));

        // `code` arrives from the OAuth callback query string, so it is untrusted
        // and must be form-encoded like redirect_uri — otherwise a crafted code
        // can inject extra form parameters into the token request.
        String tokenRequestBody = "code=" + URLEncoder.encode(code, StandardCharsets.UTF_8) +
            "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
            "&grant_type=authorization_code";

        String tokenResponse = OAuthHttp.executeForBody(HttpRequest.post(google.getTokenUrl())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Authorization", "Basic " + basicAuth)
            .body(tokenRequestBody), PROVIDER_NAME, "token exchange");

        try {
            JsonNode tokenNode = objectMapper.readTree(tokenResponse);
            String accessToken = tokenNode.path("access_token").asText(null);
            if (accessToken == null || accessToken.isBlank()) {
                throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS,
                    "Google token exchange did not return access_token");
            }
            return new OAuthTokenResponse(accessToken);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse Google token response", e);
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "OAuth token exchange failed", e);
        }
    }

    @Override
    public OAuthUserInfo fetchUserInfo(String accessToken) {
        OAuthProperties.OAuthProvider google = oauthProperties.getGoogle();

        String userResponse = OAuthHttp.executeForBody(HttpRequest.get(google.getUserUrl())
            .header("Authorization", "Bearer " + accessToken), PROVIDER_NAME, "user info");

        try {
            JsonNode userNode = objectMapper.readTree(userResponse);
            String googleId = userNode.path("id").asText(null);
            if (googleId == null || googleId.isBlank()) {
                throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS,
                    "Google user info did not return id");
            }
            String email = userNode.path("email").asText(null);
            String name = userNode.has("name") && !userNode.get("name").isNull()
                    ? userNode.get("name").asText()
                    : (email != null && email.contains("@") ? email.substring(0, email.indexOf('@')) : googleId);
            String avatar = userNode.has("picture") ? userNode.get("picture").asText() : null;

            return new OAuthUserInfo(googleId, name, email, avatar);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse Google user response", e);
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "Failed to get Google user info", e);
        }
    }
}
