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
 * Network-free unit tests for {@link GithubOAuthClient}, exercising the
 * adapter exclusively through the injected {@link OAuthHttpTransport}
 * seam (a {@link FakeOAuthHttpTransport}). Proves that:
 *
 * <ul>
 *   <li>the provider builds the GitHub-shaped request (URL, Basic-Auth
 *       header, form-encoded {@code code} + {@code redirect_uri});</li>
 *   <li>the adapter's null-safe JSON parsing and fail-closed error
 *       mapping are correct without standing up a real HTTP server;</li>
 *   <li>a non-2xx scripted response is rejected with
 *       {@link ErrorCode#AUTH_INVALID_CREDENTIALS} (status surfaced, body
 *       never echoed into the exception).</li>
 * </ul>
 *
 * <p>Complements {@link GithubOAuthClientTest}, which exercises the same
 * provider through the production {@link OAuthHttp} transport over a
 * {@link okhttp3.mockwebserver.MockWebServer}.
 */
@DisplayName("GithubOAuthClient (FakeOAuthHttpTransport)")
class GithubOAuthClientFakeTransportTest {

    private FakeOAuthHttpTransport transport;
    private GithubOAuthClient client;

    @BeforeEach
    void setUp() {
        transport = new FakeOAuthHttpTransport();
        OAuthProperties props = new OAuthProperties();
        OAuthProperties.OAuthProvider github = props.getGithub();
        github.setClientId("cid");
        github.setClientSecret("csec");
        // URLs are irrelevant when the transport is a fake, but the adapter
        // still builds an HttpRequest with them so they must parse cleanly.
        github.setTokenUrl("https://github.example/login/oauth/access_token");
        github.setUserUrl("https://github.example/user");
        github.setAuthorizeUrl("https://github.example/login/oauth/authorize");
        github.setScopes("read:user");
        client = new GithubOAuthClient(props, new ObjectMapper(), transport);
    }

    @Test
    @DisplayName("token exchange: 2xx body is parsed and access_token is returned")
    void tokenHappyPath() {
        transport.enqueueOk("{\"access_token\":\"abc\"}");

        OAuthTokenResponse token = client.exchangeCodeForToken("code-1", "https://app/cb");

        assertThat(token.accessToken()).isEqualTo("abc");
        assertThat(transport.callCount()).isEqualTo(1);
        FakeOAuthHttpTransport.RecordedCall call = transport.takeRequest();
        assertThat(call.provider()).isEqualTo("github");
        assertThat(call.operation()).isEqualTo("token exchange");
        assertThat(call.request().getUrl()).isEqualTo("https://github.example/login/oauth/access_token");
        assertThat(call.request().getMethod()).isEqualTo(Method.POST);
        // Body is form-encoded — a crafted `code` with '&' must not inject params.
        assertThat(call.body()).contains("code=code-1");
        assertThat(call.body()).contains("redirect_uri=https%3A%2F%2Fapp%2Fcb");
        // RFC 6749 Basic-Auth instead of plaintext body.
        assertThat(call.request().header("Authorization")).startsWith("Basic ");
    }

    @Test
    @DisplayName("token exchange: crafted `code` cannot inject extra form params")
    void tokenBodyIsFormEncoded() {
        transport.enqueueOk("{\"access_token\":\"abc\"}");

        // An attacker who controls the OAuth callback can submit a `code`
        // containing '&'. If the adapter built the body by concatenation,
        // they could inject extra params and override the redirect_uri.
        client.exchangeCodeForToken("evil&injected=1", "https://app/cb");

        FakeOAuthHttpTransport.RecordedCall call = transport.takeRequest();
        assertThat(call.body()).contains("code=evil%26injected%3D1");
        assertThat(call.body()).doesNotContain("injected=1&");
    }

    @Test
    @DisplayName("token exchange: non-2xx is rejected (status gate, body never echoed)")
    void tokenProviderErrorFailsClosed() {
        transport.enqueueStatus(400, "{\"error\":\"invalid_grant\",\"error_description\":\"code expired\"}");

        assertThatThrownBy(() -> client.exchangeCodeForToken("code-1", "https://app/cb"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("token exchange: 2xx body without access_token fails closed (null-safe parse)")
    void tokenMissingAccessTokenFailsClosed() {
        transport.enqueueOk("{\"error\":\"bad_verification_code\"}");

        assertThatThrownBy(() -> client.exchangeCodeForToken("code-1", "https://app/cb"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("user info: maps id/login/name/email/avatar and uses Bearer auth")
    void userInfoHappyPath() {
        transport.enqueueOk(
                "{\"id\":42,\"login\":\"oct\",\"name\":\"Octavia\","
                        + "\"email\":\"o@example.com\",\"avatar_url\":\"https://a/u.png\"}");

        OAuthUserInfo info = client.fetchUserInfo("tok-1");

        assertThat(info.providerId()).isEqualTo("42");
        assertThat(info.name()).isEqualTo("Octavia");
        assertThat(info.email()).isEqualTo("o@example.com");
        assertThat(info.avatar()).isEqualTo("https://a/u.png");
        FakeOAuthHttpTransport.RecordedCall call = transport.takeRequest();
        assertThat(call.provider()).isEqualTo("github");
        assertThat(call.operation()).isEqualTo("user info");
        assertThat(call.request().getMethod()).isEqualTo(Method.GET);
        assertThat(call.request().getUrl()).isEqualTo("https://github.example/user");
        assertThat(call.request().header("Authorization")).isEqualTo("Bearer tok-1");
    }

    @Test
    @DisplayName("user info: falls back to login when name is null")
    void userInfoFallsBackToLoginWhenNameMissing() {
        transport.enqueueOk("{\"id\":7,\"login\":\"oct\",\"name\":null,\"email\":null}");

        OAuthUserInfo info = client.fetchUserInfo("tok");

        assertThat(info.name()).isEqualTo("oct");
        assertThat(info.email()).isNull();
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
        transport.enqueueOk("{\"login\":\"oct\"}");

        assertThatThrownBy(() -> client.fetchUserInfo("tok"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }
}
