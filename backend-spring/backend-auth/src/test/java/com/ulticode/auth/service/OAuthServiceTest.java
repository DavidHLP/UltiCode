package com.ulticode.auth.service;

import com.ulticode.auth.account.AuthAccountPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.dto.AuthUserVO;
import com.ulticode.auth.dto.LoginResponse;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.error.AuthErrorCode;
import com.ulticode.auth.security.oauth.OAuthClient;
import com.ulticode.auth.security.oauth.OAuthProperties;
import com.ulticode.auth.security.oauth.OAuthStatePort;
import com.ulticode.auth.security.oauth.OAuthTokenResponse;
import com.ulticode.auth.security.oauth.OAuthUserInfo;
import com.ulticode.auth.security.oauth.mapper.OAuthProviderIdentityMapper;
import com.ulticode.auth.session.AuthSessionPort;
import com.ulticode.auth.util.FixedUuidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OAuthService (backend-auth)")
class OAuthServiceTest {

    @Mock
    private OAuthProperties oauthProperties;

    @Mock
    private AuthAccountPort accountPort;

    @Mock
    private AuthSessionPort authSessionPort;

    @Mock
    private OAuthStatePort oauthStatePort;

    @Mock
    private OAuthClient githubClient;

    @Mock
    private OAuthProviderIdentityMapper providerIdentityMapper;

    @Mock
    private Clock clock;

    private OAuthService oauthService;

    private OAuthProperties.OAuthProvider githubConfig;

    @BeforeEach
    void setUp() {
        lenient().when(clock.getZone()).thenReturn(ZoneId.systemDefault());
        lenient().when(clock.instant()).thenReturn(Instant.now());

        githubConfig = new OAuthProperties.OAuthProvider();
        githubConfig.setClientId("gh-client-123");
        githubConfig.setClientSecret("gh-secret-456");
        githubConfig.setRedirectUri("http://localhost:9001/auth/github/callback");
        githubConfig.setAuthorizeUrl("https://github.com/login/oauth/authorize");

        when(oauthProperties.getGithub()).thenReturn(githubConfig);
        when(githubClient.getProviderName()).thenReturn("github");

        oauthService = new OAuthService(
                oauthProperties, accountPort, authSessionPort, oauthStatePort,
                List.of(githubClient), new FixedUuidGenerator(), clock, providerIdentityMapper);
        oauthService.wireClientLookup();
    }

    @Nested
    @DisplayName("getGithubAuthUrl()")
    class GetGithubAuthUrl {

        @Test
        @DisplayName("issues state and delegates URL construction to client")
        void buildsAuthUrl() {
            when(oauthStatePort.issueState("github", null)).thenReturn("state-123");
            when(githubClient.buildAuthorizationUrl("state-123", "http://localhost:9001/auth/github/callback"))
                    .thenReturn("https://github.com/login/oauth/authorize?state=state-123");

            String url = oauthService.getGithubAuthUrl(null);

            assertThat(url).isEqualTo("https://github.com/login/oauth/authorize?state=state-123");
        }
    }

    @Nested
    @DisplayName("handleGithubCallback()")
    class HandleGithubCallback {

        @Test
        @DisplayName("validates state, exchanges code, and creates user when new")
        void successfulNewUserCallback() {
            when(githubClient.exchangeCodeForToken("code-abc", "http://localhost:9001/auth/github/callback"))
                    .thenReturn(new OAuthTokenResponse("token-123", "bearer", "user"));

            OAuthUserInfo userInfo = new OAuthUserInfo("1001", "octocat", "Mona Cat", "cat@github.com", true, "http://avatar.cat");
            when(githubClient.fetchUserInfo("token-123")).thenReturn(userInfo);
            when(accountPort.findByEmail("cat@github.com")).thenReturn(Optional.empty());
            when(accountPort.create(any())).thenAnswer(inv -> inv.getArgument(0));

            LoginResponse mockResponse = LoginResponse.builder()
                    .csrfToken("csrf-cat")
                    .user(new AuthUserVO("fixed-uuid-1234", "octocat", "octocat", "", "USER", true, false, ""))
                    .build();
            when(authSessionPort.completeLogin(any(), any())).thenReturn(mockResponse);

            LoginResponse response = oauthService.handleGithubCallback("code-abc", "state-123", "state-123", null);

            assertThat(response).isNotNull();
            assertThat(response.getCsrfToken()).isEqualTo("csrf-cat");
            verify(oauthStatePort, times(1)).validateAndConsume("github", "state-123", "state-123", null);
            verify(accountPort, times(1)).create(any());
        }

        @Test
        @DisplayName("refuses auto-link when email matches existing account but emailVerified is false")
        void refusesUnverifiedEmailAutoLink() {
            when(githubClient.exchangeCodeForToken("code-abc", "http://localhost:9001/auth/github/callback"))
                    .thenReturn(new OAuthTokenResponse("token-123", "bearer", "user"));

            OAuthUserInfo unverifiedUser = new OAuthUserInfo("1002", "hacker", "Hacker", "existing@example.com", false, null);
            when(githubClient.fetchUserInfo("token-123")).thenReturn(unverifiedUser);

            AuthAccountRecord existingAccount = new AuthAccountRecord(
                    "existing-user-id", "victim", "existing@example.com", "pass", "USER", true, false, null, LocalDateTime.now()
            );
            when(accountPort.findByEmail("existing@example.com")).thenReturn(Optional.of(existingAccount));

            assertThatThrownBy(() -> oauthService.handleGithubCallback("code-abc", "state-123", "state-123", null))
                    .isInstanceOf(AuthBusinessException.class)
                    .satisfies(ex -> assertThat(((AuthBusinessException) ex).getErrorCode())
                            .isEqualTo(AuthErrorCode.AUTH_INVALID_CREDENTIALS));
        }

        @Test
        @DisplayName("propagates state validation failures from OAuthStatePort")
        void throwsOnStateValidationFailure() {
            doThrow(new AuthBusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS, "State cookie mismatch"))
                    .when(oauthStatePort).validateAndConsume("github", "bad-state", "mismatched-cookie", null);

            assertThatThrownBy(() -> oauthService.handleGithubCallback("code-abc", "bad-state", "mismatched-cookie", null))
                    .isInstanceOf(AuthBusinessException.class);
        }
    }
}
