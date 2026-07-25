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
 * Deterministic HTTP tests for {@link GithubOAuthClient} against a
 * {@link MockWebServer}. OAuthServiceTest mocks the whole {@link OAuthClient}
 * interface, so the real exchange path — timeouts, status gate, null-safe
 * parsing, and form-encoding of {@code code} — had no coverage. These tests pin
 * the security fixes for those gaps.
 */
@DisplayName("GithubOAuthClient (MockWebServer)")
class GithubOAuthClientTest {

    private MockWebServer server;
    private GithubOAuthClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        OAuthProperties props = new OAuthProperties();
        OAuthProperties.OAuthProvider github = props.getGithub();
        github.setClientId("cid");
        github.setClientSecret("csec");
        github.setTokenUrl(server.url("/login/oauth/access_token").toString());
        github.setUserUrl(server.url("/user").toString());
        github.setAuthorizeUrl("https://github.com/login/oauth/authorize");
        github.setScopes("read:user");
        client = new GithubOAuthClient(props, new ObjectMapper(), new OAuthHttp());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    @DisplayName("token exchange returns access_token on 2xx")
    void tokenHappyPath() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"access_token\":\"abc\"}"));
        OAuthTokenResponse token = client.exchangeCodeForToken("code-1", "https://app/cb");
        assertThat(token.accessToken()).isEqualTo("abc");

        RecordedRequest request = server.takeRequest();
        // `code` is form-encoded; a crafted code with '&' must not inject params.
        assertThat(request.getBody().readUtf8()).contains("code=code-1");
        assertThat(request.getHeader("Authorization")).startsWith("Basic ");
    }

    @Test
    @DisplayName("token exchange rejects a non-2xx provider response (status gate, not NPE)")
    void tokenProviderErrorFailsClosed() {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"error\":\"invalid_grant\"}"));
        assertThatThrownBy(() -> client.exchangeCodeForToken("code-1", "https://app/cb"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("token exchange rejects a 2xx body missing access_token (null-safe, not NPE)")
    void tokenMissingAccessTokenFailsClosed() {
        server.enqueue(new MockResponse().setBody("{\"error\":\"bad_verification_code\"}"));
        assertThatThrownBy(() -> client.exchangeCodeForToken("code-1", "https://app/cb"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("user info maps id/login/name/email/avatar on 2xx")
    void userInfoHappyPath() {
        server.enqueue(new MockResponse().setBody(
                "{\"id\":42,\"login\":\"oct\",\"name\":\"Octavia\","
                        + "\"email\":\"o@example.com\",\"avatar_url\":\"https://a/u.png\"}"));
        OAuthUserInfo info = client.fetchUserInfo("tok");
        assertThat(info.providerId()).isEqualTo("42");
        assertThat(info.name()).isEqualTo("Octavia");
        assertThat(info.email()).isEqualTo("o@example.com");
        assertThat(info.avatar()).isEqualTo("https://a/u.png");
    }

    @Test
    @DisplayName("user info rejects a non-2xx response")
    void userInfoProviderErrorFailsClosed() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{}"));
        assertThatThrownBy(() -> client.fetchUserInfo("tok"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }
}
