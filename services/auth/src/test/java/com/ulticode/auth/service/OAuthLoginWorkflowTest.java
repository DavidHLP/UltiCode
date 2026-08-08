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
import com.ulticode.auth.security.oauth.entity.OAuthProviderIdentity;
import com.ulticode.auth.security.oauth.mapper.OAuthProviderIdentityMapper;
import com.ulticode.auth.session.AuthSession;
import com.ulticode.auth.session.AuthSessionPort;
import com.ulticode.auth.session.CookieMutation;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OAuthLoginWorkflow (backend-auth)")
class OAuthLoginWorkflowTest {

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
    private OAuthClient googleClient;

    @Mock
    private OAuthProviderIdentityMapper providerIdentityMapper;

    @Mock
    private Clock clock;

    private DefaultOAuthLoginWorkflow workflow;
    private OAuthProperties.OAuthProvider githubConfig;
    private OAuthProperties.OAuthProvider googleConfig;

    @BeforeEach
    void setUp() {
        lenient().when(clock.getZone()).thenReturn(ZoneId.systemDefault());
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-08-06T00:00:00Z"));

        githubConfig = providerConfig("http://localhost:9001/auth/github/callback");
        googleConfig = providerConfig("http://localhost:9001/auth/google/callback");
        when(oauthProperties.getGithub()).thenReturn(githubConfig);
        when(oauthProperties.getGoogle()).thenReturn(googleConfig);

        when(githubClient.getProviderName()).thenReturn("github");
        when(googleClient.getProviderName()).thenReturn("google");
        lenient().when(oauthStatePort.clearStateCookie(anyString()))
                .thenAnswer(invocation -> clearCookie(invocation.getArgument(0)));

        workflow = new DefaultOAuthLoginWorkflow(
                oauthProperties,
                accountPort,
                authSessionPort,
                oauthStatePort,
                List.of(githubClient, googleClient),
                new FixedUuidGenerator(),
                clock,
                providerIdentityMapper);
        workflow.wireClientLookup();
    }

    @Nested
    @DisplayName("begin()")
    class Begin {

        @Test
        @DisplayName("is provider-neutral and returns explicit state cookie mutation")
        void returnsAuthorizationAndStateCookie() {
            when(oauthStatePort.issueState("github"))
                    .thenReturn(new OAuthStatePort.OAuthStateIssue("state-123", stateCookie("github", "state-123")));
            when(githubClient.buildAuthorizationUrl("state-123", githubConfig.getRedirectUri()))
                    .thenReturn("https://github.com/login/oauth/authorize?state=state-123");

            OAuthLoginWorkflow.OAuthAuthorization authorization = workflow.begin("GitHub");

            assertThat(authorization.authorizationUrl())
                    .isEqualTo("https://github.com/login/oauth/authorize?state=state-123");
            assertThat(authorization.stateCookie())
                    .extracting(CookieMutation::name, CookieMutation::value, CookieMutation::maxAgeSeconds,
                            CookieMutation::httpOnly, CookieMutation::path)
                    .containsExactly("oauth_state_github", "state-123", 300, true, "/auth");
            verify(oauthStatePort).issueState("github");
        }

        @Test
        @DisplayName("rejects an unsupported provider before issuing state")
        void rejectsUnsupportedProvider() {
            assertThatThrownBy(() -> workflow.begin("gitlab"))
                    .isInstanceOf(AuthBusinessException.class)
                    .hasMessageContaining("Unsupported OAuth provider");

            verify(oauthStatePort, never()).issueState(anyString());
        }
    }

    @Nested
    @DisplayName("complete()")
    class Complete {

        @Test
        @DisplayName("validates state, creates a user, links identity, and returns all cookie mutations")
        void successfulNewUserCallback() {
            stubValidGithubCallback();
            OAuthUserInfo userInfo = new OAuthUserInfo(
                    "1001", "octocat", "Mona Cat", "cat@github.com", true, "http://avatar.cat");
            when(githubClient.fetchUserInfo("token-123")).thenReturn(userInfo);
            when(accountPort.findByEmail("cat@github.com")).thenReturn(Optional.empty());
            when(accountPort.create(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(authSessionPort.completeLogin(any())).thenReturn(mockSession("csrf-cat"));

            OAuthLoginWorkflow.OAuthCompletion completion = workflow.complete(
                    "github", "code-abc", "state-123", "state-123");

            assertThat(completion.response().getCsrfToken()).isEqualTo("csrf-cat");
            assertThat(completion.cookies())
                    .extracting(CookieMutation::name)
                    .containsExactly("oauth_state_github", "access_token", "refresh_token", "csrf_token");
            verify(oauthStatePort).validateAndConsume("github", "state-123", "state-123");
            verify(accountPort).create(any());
            verify(providerIdentityMapper).insert(any());
        }

        @Test
        @DisplayName("uses the provider identity binding before the email fallback")
        void existingBindingIsAuthoritative() {
            stubValidGithubCallback();
            when(githubClient.fetchUserInfo("token-123"))
                    .thenReturn(new OAuthUserInfo("1001", "octocat", "Mona Cat", "bound@example.com", true, null));
            OAuthProviderIdentity binding = new OAuthProviderIdentity();
            binding.setAccountId("bound-user");
            when(providerIdentityMapper.findActiveByProviderAndProviderUserId("github", "1001"))
                    .thenReturn(binding);
            AuthAccountRecord bound = account("bound-user", "bound", "bound@example.com");
            when(accountPort.findById("bound-user")).thenReturn(Optional.of(bound));
            when(authSessionPort.completeLogin(bound)).thenReturn(mockSession("csrf-bound"));

            OAuthLoginWorkflow.OAuthCompletion completion = workflow.complete(
                    "github", "code-abc", "state-123", "state-123");

            assertThat(completion.response().getCsrfToken()).isEqualTo("csrf-bound");
            verify(accountPort, never()).findByEmail(anyString());
            verify(providerIdentityMapper, never()).insert(any());
        }

        @Test
        @DisplayName("rejects unverified email auto-link and carries state cleanup")
        void refusesUnverifiedEmailAutoLink() {
            stubValidGithubCallback();
            OAuthUserInfo unverifiedUser = new OAuthUserInfo(
                    "1002", "hacker", "Hacker", "existing@example.com", false, null);
            when(githubClient.fetchUserInfo("token-123")).thenReturn(unverifiedUser);
            AuthAccountRecord existingAccount = account("existing-user-id", "victim", "existing@example.com");
            when(accountPort.findByEmail("existing@example.com")).thenReturn(Optional.of(existingAccount));

            assertThatThrownBy(() -> workflow.complete("github", "code-abc", "state-123", "state-123"))
                    .isInstanceOf(OAuthLoginWorkflow.OAuthCallbackFailure.class)
                    .satisfies(ex -> {
                        OAuthLoginWorkflow.OAuthCallbackFailure failure =
                                (OAuthLoginWorkflow.OAuthCallbackFailure) ex;
                        assertThat(failure.stateCookie().name()).isEqualTo("oauth_state_github");
                        assertThat(failure.cause())
                                .isInstanceOf(AuthBusinessException.class)
                                .satisfies(cause -> assertThat(((AuthBusinessException) cause).getErrorCode())
                                        .isEqualTo(AuthErrorCode.AUTH_INVALID_CREDENTIALS));
                    });
        }

        @Test
        @DisplayName("clears state on validation failure before preserving the original error")
        void stateFailureCarriesCleanup() {
            AuthBusinessException original = new AuthBusinessException(
                    AuthErrorCode.AUTH_INVALID_CREDENTIALS, "State cookie mismatch");
            doThrow(original).when(oauthStatePort).validateAndConsume(
                    "github", "bad-state", "mismatched-cookie");

            assertThatThrownBy(() -> workflow.complete("github", "code-abc", "bad-state", "mismatched-cookie"))
                    .isInstanceOf(OAuthLoginWorkflow.OAuthCallbackFailure.class)
                    .satisfies(ex -> {
                        OAuthLoginWorkflow.OAuthCallbackFailure failure =
                                (OAuthLoginWorkflow.OAuthCallbackFailure) ex;
                        assertThat(failure.cause()).isSameAs(original);
                        assertThat(failure.stateCookie().maxAgeSeconds()).isZero();
                    });
            verify(githubClient, never()).exchangeCodeForToken(anyString(), anyString());
        }

        @Test
        @DisplayName("clears state when provider exchange fails after validation")
        void providerFailureCarriesCleanup() {
            when(oauthStatePort.validateAndConsume("github", "state-123", "state-123"))
                    .thenReturn(clearCookie("github"));
            AuthBusinessException original = new AuthBusinessException(
                    AuthErrorCode.AUTH_INVALID_CREDENTIALS, "provider unavailable");
            when(githubClient.exchangeCodeForToken("code-abc", githubConfig.getRedirectUri()))
                    .thenThrow(original);

            assertThatThrownBy(() -> workflow.complete("github", "code-abc", "state-123", "state-123"))
                    .isInstanceOf(OAuthLoginWorkflow.OAuthCallbackFailure.class)
                    .satisfies(ex -> assertThat(((OAuthLoginWorkflow.OAuthCallbackFailure) ex).cause())
                            .isSameAs(original));
        }

        @Test
        @DisplayName("ignores only DuplicateKeyException from concurrent identity binding")
        void duplicateKeyRaceIsIgnored() {
            stubNewVerifiedUser("race@example.com");
            doThrow(new DuplicateKeyException("concurrent binding")).when(providerIdentityMapper).insert(any());

            OAuthLoginWorkflow.OAuthCompletion completion = workflow.complete(
                    "github", "code-abc", "state-123", "state-123");

            assertThat(completion.response()).isNotNull();
        }

        @Test
        @DisplayName("propagates non-duplicate persistence failures through callback cleanup")
        void nonDuplicatePersistenceFailurePropagates() {
            stubNewVerifiedUser("db@example.com");
            DataIntegrityViolationException original = new DataIntegrityViolationException("constraint failure");
            doThrow(original).when(providerIdentityMapper).insert(any());

            assertThatThrownBy(() -> workflow.complete("github", "code-abc", "state-123", "state-123"))
                    .isInstanceOf(OAuthLoginWorkflow.OAuthCallbackFailure.class)
                    .satisfies(ex -> assertThat(((OAuthLoginWorkflow.OAuthCallbackFailure) ex).cause())
                            .isSameAs(original));
        }

        private void stubValidGithubCallback() {
            when(oauthStatePort.validateAndConsume("github", "state-123", "state-123"))
                    .thenReturn(clearCookie("github"));
            when(githubClient.exchangeCodeForToken("code-abc", githubConfig.getRedirectUri()))
                    .thenReturn(new OAuthTokenResponse("token-123", "bearer", "user"));
        }

        private void stubNewVerifiedUser(String email) {
            stubValidGithubCallback();
            when(githubClient.fetchUserInfo("token-123"))
                    .thenReturn(new OAuthUserInfo("1001", "octocat", "Mona Cat", email, true, null));
            when(accountPort.findByEmail(email)).thenReturn(Optional.empty());
            when(accountPort.create(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(authSessionPort.completeLogin(any())).thenReturn(mockSession("csrf-token"));
        }
    }

    private static OAuthProperties.OAuthProvider providerConfig(String redirectUri) {
        OAuthProperties.OAuthProvider config = new OAuthProperties.OAuthProvider();
        config.setRedirectUri(redirectUri);
        return config;
    }

    private static CookieMutation stateCookie(String provider, String state) {
        return new CookieMutation("oauth_state_" + provider, state, 300, true, true, "/auth");
    }

    private static CookieMutation clearCookie(String provider) {
        return new CookieMutation("oauth_state_" + provider, "", 0, true, true, "/auth");
    }

    private static AuthSession mockSession(String csrfToken) {
        return new AuthSession(
                LoginResponse.builder()
                        .csrfToken(csrfToken)
                        .user(new AuthUserVO("fixed-uuid-1234", "octocat", "octocat", "", "USER", true, false, ""))
                        .build(),
                List.of(
                        CookieMutation.set("access_token", "access", 900, true),
                        CookieMutation.set("refresh_token", "refresh", 604800, true),
                        CookieMutation.set("csrf_token", csrfToken, 900, false))
        );
    }

    private static AuthAccountRecord account(String id, String username, String email) {
        return new AuthAccountRecord(
                id, username, email, "pass", "USER", true, false, null,
                LocalDateTime.parse("2026-08-06T00:00:00"));
    }
}
