package com.ulticode.modules.auth.service.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.security.oauth.OAuthProperties;
import cn.hutool.http.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Network-free unit tests for {@link GoogleOAuthClient}, exercising the
 * adapter exclusively through the injected {@link OAuthHttpTransport}
 * seam (a {@link FakeOAuthHttpTransport}). Proves that:
 *
 * <ul>
 *   <li>the provider builds the Google-shaped request (URL, Basic-Auth
 *       header, Content-Type, form-encoded {@code code} +
 *       {@code redirect_uri} + {@code grant_type});</li>
 *   <li>the adapter's null-safe JSON parsing and fail-closed error
 *       mapping are correct without standing up a real HTTP server;</li>
 *   <li>a non-2xx scripted response is rejected with
 *       {@link ErrorCode#AUTH_INVALID_CREDENTIALS}.</li>
 * </ul>
 *
 * <p>Complements {@link GoogleOAuthClientTest}, which exercises the same
 * provider through the production {@link OAuthHttp} transport over a
 * {@link okhttp3.mockwebserver.MockWebServer}.
 */
@DisplayName("GoogleOAuthClient (FakeOAuthHttpTransport)")
class GoogleOAuthClientFakeTransportTest {

    private FakeOAuthHttpTransport transport;
    private GoogleOAuthClient client;

    @BeforeEach
    void setUp() {
        transport = new FakeOAuthHttpTransport();
        OAuthProperties props = new OAuthProperties();
        OAuthProperties.OAuthProvider google = props.getGoogle();
        google.setClientId("cid");
        google.setClientSecret("csec");
        google.setTokenUrl("https://oauth2.googleapis.com/token");
        google.setUserUrl("https://openidconnect.googleapis.com/v1/userinfo");
        google.setAuthorizeUrl("https://accounts.google.com/o/oauth2/v2/auth");
        google.setScopes("openid email profile");
        client = new GoogleOAuthClient(props, new ObjectMapper(), transport);
    }

    @Test
    @DisplayName("token exchange: 2xx body is parsed and access_token is returned")
    void tokenHappyPath() {
        transport.enqueueOk("{\"access_token\":\"xyz\",\"token_type\":\"Bearer\"}");

        OAuthTokenResponse token = client.exchangeCodeForToken("code-1", "https://app/cb");

        assertThat(token.accessToken()).isEqualTo("xyz");
        FakeOAuthHttpTransport.RecordedCall call = transport.takeRequest();
        assertThat(call.provider()).isEqualTo("google");
        assertThat(call.operation()).isEqualTo("token exchange");
        assertThat(call.request().getUrl()).isEqualTo("https://oauth2.googleapis.com/token");
        assertThat(call.request().getMethod()).isEqualTo(Method.POST);
        // Google sends grant_type alongside the code — a crafted code must
        // not be able to override the form-body shape.
        assertThat(call.body()).contains("code=code-1");
        assertThat(call.body()).contains("redirect_uri=https%3A%2F%2Fapp%2Fcb");
        assertThat(call.body()).contains("grant_type=authorization_code");
        assertThat(call.request().header("Authorization")).startsWith("Basic ");
        assertThat(call.request().header("Content-Type"))
                .isEqualTo("application/x-www-form-urlencoded");
    }

    @Test
    @DisplayName("token exchange: crafted `code` cannot inject extra form params")
    void tokenBodyIsFormEncoded() {
        transport.enqueueOk("{\"access_token\":\"xyz\"}");

        client.exchangeCodeForToken("evil&injected=1", "https://app/cb");

        FakeOAuthHttpTransport.RecordedCall call = transport.takeRequest();
        assertThat(call.body()).contains("code=evil%26injected%3D1");
        // grant_type must remain fixed, not be overridden by a crafted code.
        assertThat(call.body()).contains("&grant_type=authorization_code");
    }

    @Test
    @DisplayName("token exchange: non-2xx is rejected (status gate)")
    void tokenProviderErrorFailsClosed() {
        transport.enqueueStatus(401, "{\"error\":\"invalid_client\"}");

        assertThatThrownBy(() -> client.exchangeCodeForToken("code-1", "https://app/cb"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("token exchange: 2xx body without access_token fails closed (null-safe parse)")
    void tokenMissingAccessTokenFailsClosed() {
        transport.enqueueOk("{\"error\":\"invalid_grant\"}");

        assertThatThrownBy(() -> client.exchangeCodeForToken("code-1", "https://app/cb"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("user info: maps id/email/name/picture and uses Bearer auth")
    void userInfoHappyPath() {
        transport.enqueueOk(
                "{\"id\":\"gid-1\",\"email\":\"u@example.com\",\"name\":\"Una\","
                        + "\"picture\":\"https://a/p.png\"}");

        OAuthUserInfo info = client.fetchUserInfo("tok-1");

        assertThat(info.providerId()).isEqualTo("gid-1");
        assertThat(info.email()).isEqualTo("u@example.com");
        assertThat(info.name()).isEqualTo("Una");
        assertThat(info.avatar()).isEqualTo("https://a/p.png");
        FakeOAuthHttpTransport.RecordedCall call = transport.takeRequest();
        assertThat(call.request().getMethod()).isEqualTo(Method.GET);
        assertThat(call.request().getUrl()).isEqualTo("https://openidconnect.googleapis.com/v1/userinfo");
        assertThat(call.request().header("Authorization")).isEqualTo("Bearer tok-1");
    }

    @Test
    @DisplayName("user info: falls back to the email local-part when name is missing")
    void userInfoFallsBackToEmailLocalPartWhenNameMissing() {
        transport.enqueueOk("{\"id\":\"gid-1\",\"email\":\"u@example.com\"}");

        OAuthUserInfo info = client.fetchUserInfo("tok");

        assertThat(info.name()).isEqualTo("u");
    }

    @Test
    @DisplayName("user info: falls back to provider id when both name and email are missing/null")
    void userInfoFallsBackToProviderIdWhenBothMissing() {
        transport.enqueueOk("{\"id\":\"gid-1\",\"name\":null,\"email\":null}");

        OAuthUserInfo info = client.fetchUserInfo("tok");

        // `email` is null so the email-local-part branch is skipped;
        // we must not NPE and must still produce a stable display name.
        assertThat(info.name()).isEqualTo("gid-1");
    }

    @Test
    @DisplayName("user info: email without '@' does not NPE on the fallback branch")
    void userInfoEmailWithoutAtDoesNotNpe() {
        transport.enqueueOk("{\"id\":\"gid-1\",\"email\":\"no-at-sign\",\"name\":null}");

        OAuthUserInfo info = client.fetchUserInfo("tok");

        // The fallback contains('email', '@') guard protects this — we
        // pin it so a future refactor that drops the guard is caught.
        assertThat(info.name()).isEqualTo("gid-1");
    }

    @Test
    @DisplayName("user info: non-2xx is rejected (status gate)")
    void userInfoProviderErrorFailsClosed() {
        transport.enqueueStatus(401, "{}");

        assertThatThrownBy(() -> client.fetchUserInfo("tok"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("user info: 2xx body without id fails closed (null-safe parse)")
    void userInfoMissingIdFailsClosed() {
        transport.enqueueOk("{\"email\":\"u@example.com\"}");

        assertThatThrownBy(() -> client.fetchUserInfo("tok"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }
}
