package com.ulticode.modules.auth.service.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.security.oauth.OAuthProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deterministic HTTP tests for {@link GoogleOAuthClient} against a
 * {@link MockWebServer}. See {@link GithubOAuthClientTest} for why these exist;
 * Google additionally carries {@code grant_type} on the token body and splits
 * the username from {@code email} when {@code name} is absent.
 */
@DisplayName("GoogleOAuthClient (MockWebServer)")
class GoogleOAuthClientTest {

    private MockWebServer server;
    private GoogleOAuthClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        OAuthProperties props = new OAuthProperties();
        OAuthProperties.OAuthProvider google = props.getGoogle();
        google.setClientId("cid");
        google.setClientSecret("csec");
        google.setTokenUrl(server.url("/token").toString());
        google.setUserUrl(server.url("/userinfo").toString());
        google.setAuthorizeUrl("https://accounts.google.com/o/oauth2/v2/auth");
        google.setScopes("openid email profile");
        client = new GoogleOAuthClient(props, new ObjectMapper(), new OAuthHttp());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    @DisplayName("token exchange returns access_token on 2xx and sends grant_type")
    void tokenHappyPath() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"access_token\":\"abc\"}"));
        OAuthTokenResponse token = client.exchangeCodeForToken("code-1", "https://app/cb");
        assertThat(token.accessToken()).isEqualTo("abc");

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("code=code-1").contains("grant_type=authorization_code");
    }

    @Test
    @DisplayName("token exchange rejects a non-2xx provider response (status gate, not NPE)")
    void tokenProviderErrorFailsClosed() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"error\":\"invalid_client\"}"));
        assertThatThrownBy(() -> client.exchangeCodeForToken("code-1", "https://app/cb"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("token exchange rejects a 2xx body missing access_token (null-safe, not NPE)")
    void tokenMissingAccessTokenFailsClosed() {
        server.enqueue(new MockResponse().setBody("{\"error\":\"invalid_grant\"}"));
        assertThatThrownBy(() -> client.exchangeCodeForToken("code-1", "https://app/cb"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("user info maps id/email/name/picture on 2xx")
    void userInfoHappyPath() {
        server.enqueue(new MockResponse().setBody(
                "{\"id\":\"g-1\",\"email\":\"u@example.com\",\"name\":\"Ursula\","
                        + "\"picture\":\"https://a/p.png\"}"));
        OAuthUserInfo info = client.fetchUserInfo("tok");
        assertThat(info.providerId()).isEqualTo("g-1");
        assertThat(info.email()).isEqualTo("u@example.com");
        assertThat(info.name()).isEqualTo("Ursula");
        assertThat(info.avatar()).isEqualTo("https://a/p.png");
    }

    @Test
    @DisplayName("user info falls back to the email local-part when name is absent (no NPE)")
    void userInfoNameFallbackFromEmail() {
        server.enqueue(new MockResponse().setBody(
                "{\"id\":\"g-1\",\"email\":\"local@example.com\"}"));
        OAuthUserInfo info = client.fetchUserInfo("tok");
        assertThat(info.name()).isEqualTo("local");
    }

    @Test
    @DisplayName("user info rejects a non-2xx response")
    void userInfoProviderErrorFailsClosed() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("{}"));
        assertThatThrownBy(() -> client.fetchUserInfo("tok"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }
}
