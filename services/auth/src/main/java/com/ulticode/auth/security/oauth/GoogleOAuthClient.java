package com.ulticode.auth.security.oauth;

import cn.hutool.http.HttpRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.error.AuthErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Google adapter for {@link OAuthClient} inside backend-auth.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleOAuthClient implements OAuthClient {

    private static final String PROVIDER_NAME = "google";

    private final OAuthProperties oauthProperties;
    private final ObjectMapper objectMapper;
    private final OAuthHttpTransport transport;

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public String buildAuthorizationUrl(String state, String redirectUri) {
        OAuthProperties.OAuthProvider config = oauthProperties.getGoogle();
        return UriComponentsBuilder.fromHttpUrl(config.getAuthorizeUrl())
                .queryParam("client_id", config.getClientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", config.getScopes())
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    @Override
    public OAuthTokenResponse exchangeCodeForToken(String code, String redirectUri) {
        OAuthProperties.OAuthProvider config = oauthProperties.getGoogle();
        String basicAuth = Base64.getEncoder().encodeToString(
                (config.getClientId() + ":" + config.getClientSecret()).getBytes(StandardCharsets.UTF_8));

        String url = config.getTokenUrl() + "?grant_type=authorization_code"
                + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.post(url)
                .header("Authorization", "Basic " + basicAuth)
                .header("Accept", "application/json");

        String body = transport.executeForBody(req, PROVIDER_NAME, "token exchange");

        try {
            JsonNode root = objectMapper.readTree(body);
            String accessToken = root.path("access_token").asText(null);
            String tokenType = root.path("token_type").asText("bearer");
            String scope = root.path("scope").asText(null);

            if (accessToken == null || accessToken.isBlank()) {
                log.warn("Google token response missing access_token");
                throw new AuthBusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS,
                        "Google token exchange returned empty access_token");
            }

            return new OAuthTokenResponse(accessToken, tokenType, scope);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse Google token response", e);
            throw new AuthBusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS,
                    "Malformed JSON from Google token endpoint");
        }
    }

    @Override
    public OAuthUserInfo fetchUserInfo(String accessToken) {
        OAuthProperties.OAuthProvider config = oauthProperties.getGoogle();
        HttpRequest req = HttpRequest.get(config.getUserUrl())
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json");

        String body = transport.executeForBody(req, PROVIDER_NAME, "user info fetch");

        try {
            JsonNode root = objectMapper.readTree(body);
            String id = root.path("id").asText(null);
            String email = root.path("email").asText(null);
            boolean emailVerified = root.path("verified_email").asBoolean(false);
            String name = root.path("name").asText(null);
            String picture = root.path("picture").asText(null);

            String username = null;
            if (email != null && email.contains("@")) {
                username = email.substring(0, email.indexOf('@'));
            }
            if (name == null || name.isBlank()) {
                name = username;
            }

            return new OAuthUserInfo(id, username, name, email, emailVerified, picture);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse Google user info response", e);
            throw new AuthBusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS,
                    "Malformed JSON from Google user endpoint");
        }
    }
}
