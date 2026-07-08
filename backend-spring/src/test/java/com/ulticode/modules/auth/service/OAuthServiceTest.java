package com.ulticode.modules.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.auth.dto.LoginResponse;
import com.ulticode.modules.auth.session.AuthSessionPort;
import com.ulticode.modules.auth.session.OAuthStatePort;
import com.ulticode.modules.auth.service.oauth.GithubOAuthClient;
import com.ulticode.modules.auth.service.oauth.GoogleOAuthClient;
import com.ulticode.modules.auth.service.oauth.OAuthClient;
import com.ulticode.modules.auth.service.oauth.OAuthTokenResponse;
import com.ulticode.modules.auth.service.oauth.OAuthUserInfo;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import com.ulticode.security.oauth.OAuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the refactored {@link OAuthService} coordinator.
 *
 * <p>Before the port extraction, this file did not exist — the old
 * 221-LoC {@code OAuthService} fused OAuth URL building, Basic-Auth
 * header construction, token exchange HTTP calls, user-info JSON parsing,
 * and the DB user-upsert into one untested class. After the extraction,
 * the coordinator's whole job is: (1) issue/consume the OAuth state via
 * {@link OAuthStatePort}, (2) pick the matching {@link OAuthClient} by
 * {@link OAuthClient#getProviderName()}, (3) run the DB upsert tail and
 * delegate the post-auth session wiring to {@link AuthSessionPort}.
 *
 * <p>These tests pin all three contracts with mapper-style mocks. The
 * {@code OAuthClient} list is mocked so the test can verify the
 * coordinator dispatches to the right provider without any real HTTP
 * call. The state port and session port are mocked so the coordinator's
 * boundaries are exercised end-to-end. The {@code UserMapper} is mocked
 * so the DB upsert tail can be verified without a real database.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OAuthService")
class OAuthServiceTest {

    @Mock
    private OAuthProperties oauthProperties;

    @Mock
    private UserMapper userMapper;

    @Mock
    private AuthSessionPort authSessionPort;

    @Mock
    private OAuthStatePort oauthStatePort;

    @Mock
    private Clock clock;

    @Mock
    private OAuthClient githubClient;

    @Mock
    private OAuthClient googleClient;

    @InjectMocks
    private OAuthService oauthService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    private static final String GITHUB_REDIRECT = "http://localhost:9001/auth/github/callback";
    private static final String GOOGLE_REDIRECT = "http://localhost:9001/auth/google/callback";
    private static final String STATE = "test-state-123";
    private static final String CODE = "test-code-abc";
    private static final String ACCESS_TOKEN = "gh-access-token-xyz";
    private static final String GITHUB_ID = "12345";
    private static final String GOOGLE_ID = "67890";
    private static final String EMAIL = "alice@example.com";
    private static final String NAME = "Alice Example";
    private static final String AVATAR = "https://cdn.example.com/a.png";

    @BeforeEach
    void setUp() {
        // Make the mocks behave like real adapters for getProviderName().
        when(githubClient.getProviderName()).thenReturn("github");
        when(googleClient.getProviderName()).thenReturn("google");

        // Configure provider config beans (only the redirect URIs are read
        // by the coordinator's redirectUriFor lookup).
        OAuthProperties.OAuthProvider githubProps = new OAuthProperties.OAuthProvider();
        githubProps.setRedirectUri(GITHUB_REDIRECT);
        OAuthProperties.OAuthProvider googleProps = new OAuthProperties.OAuthProvider();
        googleProps.setRedirectUri(GOOGLE_REDIRECT);
        when(oauthProperties.getGithub()).thenReturn(githubProps);
        when(oauthProperties.getGoogle()).thenReturn(googleProps);

        // Clock default; specific tests override as needed.
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-01-15T10:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneId.of("UTC"));

        // Wire the (private) list field with our two fake clients and
        // rebuild the lookup table. Production wiring is Spring's
        // List<OAuthClient> autowire plus @PostConstruct.
        try {
            java.lang.reflect.Field listField = OAuthService.class.getDeclaredField("oauthClients");
            listField.setAccessible(true);
            listField.set(oauthService, List.of(githubClient, googleClient));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to wire test OAuthClient list", e);
        }
        oauthService.wireClientLookup();
    }

    private LoginResponse stubSessionResponse() {
        return LoginResponse.builder().csrfToken("csrf-stub").build();
    }

    // ==================== getGithubAuthUrl / getGoogleAuthUrl ====================

    @Nested
    @DisplayName("build authorization URL")
    class BuildAuthUrl {

        @Test
        @DisplayName("GitHub: issues state, delegates URL build to github OAuthClient")
        void github() {
            when(oauthStatePort.issueState("github", null)).thenReturn(STATE);
            when(githubClient.buildAuthorizationUrl(STATE, GITHUB_REDIRECT))
                .thenReturn("https://github.com/login/oauth/authorize?state=" + STATE);

            String url = oauthService.getGithubAuthUrl(null);

            assertThat(url).isEqualTo(
                "https://github.com/login/oauth/authorize?state=" + STATE);
            verify(oauthStatePort).issueState("github", null);
            verify(githubClient).buildAuthorizationUrl(STATE, GITHUB_REDIRECT);
            verify(googleClient, never()).buildAuthorizationUrl(anyString(), anyString());
        }

        @Test
        @DisplayName("Google: issues state, delegates URL build to google OAuthClient")
        void google() {
            when(oauthStatePort.issueState("google", null)).thenReturn(STATE);
            when(googleClient.buildAuthorizationUrl(STATE, GOOGLE_REDIRECT))
                .thenReturn("https://accounts.google.com/o/oauth2/v2/auth?state=" + STATE);

            String url = oauthService.getGoogleAuthUrl(null);

            assertThat(url).isEqualTo(
                "https://accounts.google.com/o/oauth2/v2/auth?state=" + STATE);
            verify(oauthStatePort).issueState("google", null);
            verify(googleClient).buildAuthorizationUrl(STATE, GOOGLE_REDIRECT);
            verify(githubClient, never()).buildAuthorizationUrl(anyString(), anyString());
        }
    }

    // ==================== handleCallback: dispatch + state + DB tail ====================

    @Nested
    @DisplayName("handle callback")
    class HandleCallback {

        @Test
        @DisplayName("GitHub: state validate → exchange → fetch → upsert → completeLogin")
        void github_newUser_createsAndCompletes() {
            OAuthUserInfo userInfo = new OAuthUserInfo(GITHUB_ID, NAME, EMAIL, AVATAR);
            when(githubClient.exchangeCodeForToken(CODE, GITHUB_REDIRECT))
                .thenReturn(new OAuthTokenResponse(ACCESS_TOKEN));
            when(githubClient.fetchUserInfo(ACCESS_TOKEN)).thenReturn(userInfo);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(authSessionPort.completeLogin(any(User.class), eq(null)))
                .thenReturn(stubSessionResponse());

            LoginResponse response = oauthService.handleGithubCallback(CODE, STATE, null);

            assertThat(response).isNotNull();
            assertThat(response.getCsrfToken()).isEqualTo("csrf-stub");

            // State validation always runs first (security invariant #5).
            verify(oauthStatePort).validateAndConsume("github", STATE, null);
            // Provider dispatch: only the github client sees HTTP calls.
            verify(githubClient).exchangeCodeForToken(CODE, GITHUB_REDIRECT);
            verify(githubClient).fetchUserInfo(ACCESS_TOKEN);
            verify(googleClient, never()).exchangeCodeForToken(anyString(), anyString());
            verify(googleClient, never()).fetchUserInfo(anyString());

            // DB upsert tail: selectOne by email then insert a new user.
            verify(userMapper).selectOne(any(LambdaQueryWrapper.class));
            verify(userMapper).insert(userCaptor.capture());
            verify(userMapper, never()).updateById(any(User.class));

            User created = userCaptor.getValue();
            assertThat(created.getUsername()).isEqualTo("github_" + GITHUB_ID);
            assertThat(created.getName()).isEqualTo(NAME);
            assertThat(created.getEmail()).isEqualTo(EMAIL);
            assertThat(created.getAvatar()).isEqualTo(AVATAR);
            assertThat(created.getRole()).isEqualTo("USER");
            assertThat(created.getIsActive()).isTrue();
            assertThat(created.getIsBanned()).isFalse();
            assertThat(created.getJoinedAt())
                .isEqualTo(LocalDateTime.of(2026, 1, 15, 10, 0, 0));
        }

        @Test
        @DisplayName("GitHub: existing user with changed avatar triggers updateById")
        void github_existingUser_changedAvatar_updates() {
            OAuthUserInfo userInfo = new OAuthUserInfo(GITHUB_ID, NAME, EMAIL, AVATAR);
            User existing = new User();
            existing.setId("u-existing");
            existing.setUsername("github_" + GITHUB_ID);
            existing.setEmail(EMAIL);
            existing.setAvatar("https://old.example.com/old.png");
            existing.setRole("USER");

            when(githubClient.exchangeCodeForToken(CODE, GITHUB_REDIRECT))
                .thenReturn(new OAuthTokenResponse(ACCESS_TOKEN));
            when(githubClient.fetchUserInfo(ACCESS_TOKEN)).thenReturn(userInfo);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
            when(authSessionPort.completeLogin(any(User.class), eq(null)))
                .thenReturn(stubSessionResponse());

            oauthService.handleGithubCallback(CODE, STATE, null);

            verify(userMapper).updateById(userCaptor.capture());
            assertThat(userCaptor.getValue().getAvatar()).isEqualTo(AVATAR);
            verify(userMapper, never()).insert(any(User.class));
        }

        @Test
        @DisplayName("GitHub: existing user with same avatar skips updateById")
        void github_existingUser_sameAvatar_noUpdate() {
            OAuthUserInfo userInfo = new OAuthUserInfo(GITHUB_ID, NAME, EMAIL, AVATAR);
            User existing = new User();
            existing.setId("u-existing");
            existing.setEmail(EMAIL);
            existing.setAvatar(AVATAR);

            when(githubClient.exchangeCodeForToken(CODE, GITHUB_REDIRECT))
                .thenReturn(new OAuthTokenResponse(ACCESS_TOKEN));
            when(githubClient.fetchUserInfo(ACCESS_TOKEN)).thenReturn(userInfo);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
            when(authSessionPort.completeLogin(any(User.class), eq(null)))
                .thenReturn(stubSessionResponse());

            oauthService.handleGithubCallback(CODE, STATE, null);

            verify(userMapper, never()).insert(any(User.class));
            verify(userMapper, never()).updateById(any(User.class));
        }

        @Test
        @DisplayName("Google: dispatches to google OAuthClient and uses google redirect")
        void google_dispatchesToGoogleClient() {
            OAuthUserInfo userInfo = new OAuthUserInfo(GOOGLE_ID, NAME, EMAIL, AVATAR);
            when(googleClient.exchangeCodeForToken(CODE, GOOGLE_REDIRECT))
                .thenReturn(new OAuthTokenResponse("g-access-token"));
            when(googleClient.fetchUserInfo("g-access-token")).thenReturn(userInfo);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(authSessionPort.completeLogin(any(User.class), eq(null)))
                .thenReturn(stubSessionResponse());

            LoginResponse response = oauthService.handleGoogleCallback(CODE, STATE, null);

            assertThat(response).isNotNull();
            verify(oauthStatePort).validateAndConsume("google", STATE, null);
            verify(googleClient).exchangeCodeForToken(CODE, GOOGLE_REDIRECT);
            verify(googleClient).fetchUserInfo("g-access-token");
            verify(githubClient, never()).exchangeCodeForToken(anyString(), anyString());
            verify(githubClient, never()).fetchUserInfo(anyString());

            verify(userMapper).insert(userCaptor.capture());
            assertThat(userCaptor.getValue().getUsername()).isEqualTo("google_" + GOOGLE_ID);
        }

        @Test
        @DisplayName("state validation failure short-circuits before any HTTP call")
        void github_stateFailure_noHttpCalls() {
            doThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "bad state"))
                .when(oauthStatePort).validateAndConsume(eq("github"), eq(STATE), any());

            assertThatThrownBy(() -> oauthService.handleGithubCallback(CODE, STATE, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.UNAUTHORIZED));

            verify(githubClient, never()).exchangeCodeForToken(anyString(), anyString());
            verify(githubClient, never()).fetchUserInfo(anyString());
            verify(userMapper, never()).insert(any(User.class));
            verify(userMapper, never()).selectOne(any(LambdaQueryWrapper.class));
            verify(authSessionPort, never()).completeLogin(any(User.class), any());
        }

        @Test
        @DisplayName("MockHttpServletResponse is forwarded through to state and session ports")
        void callback_forwardsResponseToStateAndSessionPorts() {
            MockHttpServletResponse response = new MockHttpServletResponse();
            OAuthUserInfo userInfo = new OAuthUserInfo(GITHUB_ID, NAME, EMAIL, AVATAR);

            when(githubClient.exchangeCodeForToken(CODE, GITHUB_REDIRECT))
                .thenReturn(new OAuthTokenResponse(ACCESS_TOKEN));
            when(githubClient.fetchUserInfo(ACCESS_TOKEN)).thenReturn(userInfo);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(authSessionPort.completeLogin(any(User.class), eq(response)))
                .thenReturn(stubSessionResponse());

            oauthService.handleGithubCallback(CODE, STATE, response);

            verify(oauthStatePort).validateAndConsume("github", STATE, response);
            verify(authSessionPort).completeLogin(any(User.class), eq(response));
        }
    }

    // ==================== wiring: client-by-name lookup ====================

    @Nested
    @DisplayName("wiring")
    class Wiring {

        @Test
        @DisplayName("empty client list → 400 BAD_REQUEST on first call")
        void emptyList_throwsBadRequest() {
            OAuthService bare = new OAuthService(
                oauthProperties, userMapper, authSessionPort, oauthStatePort,
                List.of(), clock);
            bare.wireClientLookup();

            assertThatThrownBy(() -> bare.getGithubAuthUrl(null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.BAD_REQUEST));
        }

        @Test
        @DisplayName("duplicate client for the same provider name fails fast on wire")
        void duplicateClient_failsFast() {
            OAuthClient anotherGithub = new GithubOAuthClient(
                oauthProperties, new com.fasterxml.jackson.databind.ObjectMapper());
            OAuthService dup = new OAuthService(
                oauthProperties, userMapper, authSessionPort, oauthStatePort,
                List.of(githubClient, anotherGithub), clock);

            assertThatThrownBy(dup::wireClientLookup)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate OAuthClient")
                .hasMessageContaining("github");
        }

        @Test
        @DisplayName("production adapters register with the expected provider keys")
        void realAdapters_register() {
            // Sanity check that the real adapter classes — not just mocks —
            // produce the expected provider keys. Guards against accidental
            // rename of the provider-name contract.
            assertThat(new GithubOAuthClient(
                oauthProperties, new com.fasterxml.jackson.databind.ObjectMapper()).getProviderName())
                .isEqualTo("github");
            assertThat(new GoogleOAuthClient(
                oauthProperties, new com.fasterxml.jackson.databind.ObjectMapper()).getProviderName())
                .isEqualTo("google");
        }
    }
}
