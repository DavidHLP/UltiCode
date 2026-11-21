package com.ulticode.auth.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ulticode.auth.account.*;
import com.ulticode.auth.dto.*;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.error.AuthErrorCode;
import com.ulticode.auth.refreshtoken.service.RefreshTokenService;
import com.ulticode.auth.security.jwt.JwtProperties;
import com.ulticode.auth.session.*;
import com.ulticode.auth.util.FixedUuidGenerator;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AccountSessionServiceTest {
    private final RefreshTokenService tokens = mock(RefreshTokenService.class);
    private final AuthAccountPort accounts = mock(AuthAccountPort.class);
    private final AuthSessionPort issuer = mock(AuthSessionPort.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtProperties properties = new JwtProperties();
    private AccountSessionService service;

    @BeforeEach
    void setUp() {
        properties.getCookie().getRefreshToken().setSecure(true);
        properties.getCookie().getRefreshToken().setDomain("example.test");
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        var workflow = new DefaultAuthenticationWorkflow(
                accounts, passwordEncoder, tokens, issuer, new FixedUuidGenerator(), clock);
        service = new AccountSessionService(workflow, tokens, accounts, properties, clock);
    }

    @Test
    void addDemotesPreviousSessionWithoutReturningItsCredentialToJavascript() {
        when(tokens.findValidUserId("alice-token")).thenReturn("alice");
        when(accounts.findById("alice")).thenReturn(Optional.of(account("alice", "USER", true, false)));
        AuthAccountRecord bob = account("bob", "USER", true, false);
        when(accounts.findByUsername("bob")).thenReturn(Optional.of(bob));
        when(passwordEncoder.matches("password", bob.password())).thenReturn(true);
        when(issuer.completeLogin(bob)).thenReturn(session("bob", "USER"));
        AuthSession result = service.add("bob", "password", Map.of("refresh_token", "alice-token"), false);
        assertThat(result.response().getUser().id()).isEqualTo("bob");
        assertThat(result.cookies()).anySatisfy(cookie -> {
            assertThat(cookie.name()).isEqualTo("saved_account_YWxpY2U");
            assertThat(cookie.value()).isEqualTo("alice-token");
            assertThat(cookie.httpOnly()).isTrue();
            assertThat(cookie.secure()).isTrue();
            assertThat(cookie.domain()).isEqualTo("example.test");
        });
        verify(tokens, never()).revokePresentedToken("alice-token");
    }

    @Test
    void switchingRotatesTargetAndMovesCurrentCredentialOutOfTheActiveSlot() {
        when(tokens.verifiedUserId("alice-token")).thenReturn("alice");
        when(tokens.findValidUserId("bob-token")).thenReturn("bob");
        AuthAccountRecord alice = account("alice", "SUPER_ADMIN", true, false);
        when(accounts.findById("alice")).thenReturn(Optional.of(alice));
        when(tokens.validateAndRotate("alice-token"))
                .thenReturn(new RefreshTokenService.RotationResult("alice", "rotated-token"));
        when(issuer.completeRefresh(alice, "rotated-token")).thenReturn(session("alice", "SUPER_ADMIN"));
        AuthSession result = service.switchTo("alice",
                Map.of("refresh_token", "bob-token", "saved_account_YWxpY2U", "alice-token"), true);
        assertThat(result.cookies()).anyMatch(c -> c.name().equals("saved_account_Ym9i") && c.value().equals("bob-token") && c.httpOnly());
        assertThat(result.cookies()).anyMatch(c -> c.name().equals("saved_account_YWxpY2U") && c.maxAgeSeconds() == 0);
        assertThat(result.response().getUser().id()).isEqualTo("alice");
    }

    @Test
    void targetIdCannotSelectAnotherAccountsToken() {
        when(tokens.verifiedUserId("forged")).thenReturn("bob");
        assertThatThrownBy(() -> service.switchTo("alice", Map.of("saved_account_YWxpY2U", "forged"), false))
                .isInstanceOf(AuthBusinessException.class);
        verify(tokens, never()).validateAndRotate(any());
    }

    @Test
    void revokedSavedTokenStillReachesTheExistingReplayDetection() {
        when(tokens.verifiedUserId("replayed")).thenReturn("alice");
        when(accounts.findById("alice")).thenReturn(Optional.of(account("alice", "USER", true, false)));
        when(tokens.validateAndRotate("replayed")).thenThrow(new AuthBusinessException(AuthErrorCode.AUTH_TOKEN_EXPIRED));
        assertThatThrownBy(() -> service.switchTo("alice", Map.of("saved_account_YWxpY2U", "replayed"), false))
                .isInstanceOf(AuthBusinessException.class);
        verify(tokens).validateAndRotate("replayed");
    }

    @Test
    void invalidOrExpiredSavedSessionCannotBeSwitched() {
        assertThatThrownBy(() -> service.switchTo("alice", Map.of(), false)).isInstanceOf(AuthBusinessException.class);
        verify(tokens, never()).validateAndRotate(any());
    }

    @Test
    void adminSwitchRejectsUserBeforeRotatingTheirSession() {
        when(tokens.verifiedUserId("alice-token")).thenReturn("alice");
        when(accounts.findById("alice")).thenReturn(Optional.of(account("alice", "USER", true, false)));
        assertThatThrownBy(() -> service.switchTo("alice", Map.of("saved_account_YWxpY2U", "alice-token"), true))
                .isInstanceOfSatisfying(AuthBusinessException.class,
                        failure -> assertThat(failure.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_INVALID_CREDENTIALS));
        verify(tokens, never()).validateAndRotate(any());
    }

    @Test
    void catalogRejectsMismatchedNamesAndInactiveOrBannedAccounts() {
        when(tokens.findValidUserId("alice-token")).thenReturn("alice");
        when(tokens.findValidUserId("bob-token")).thenReturn("bob");
        when(accounts.findById("alice")).thenReturn(Optional.of(account("alice", "USER", false, false)));
        when(accounts.findById("bob")).thenReturn(Optional.of(account("bob", "USER", true, true)));
        assertThat(service.list(Map.of("saved_account_YWxpY2U", "alice-token", "saved_account_Ym9i", "bob-token"))).isEmpty();
        assertThat(service.list(Map.of("saved_account_d3Jvbmc", "alice-token"))).isEmpty();
    }

    @Test
    void catalogReportsCurrentAccountAndNeverReturnsTokens() {
        when(tokens.findValidUserId("alice-token")).thenReturn("alice");
        when(accounts.findById("alice")).thenReturn(Optional.of(account("alice", "USER", true, false)));
        assertThat(service.list(Map.of("refresh_token", "alice-token")))
                .containsExactly(new AccountSessionService.Account("alice", "alice", "alice@example.test", "USER", true));
    }

    @Test
    void sixthAccountIsRejectedBeforeAuthenticating() {
        Map<String, String> cookies = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            String id = "user" + i;
            String token = "token" + i;
            when(tokens.findValidUserId(token)).thenReturn(id);
            when(accounts.findById(id)).thenReturn(Optional.of(account(id, "USER", true, false)));
            cookies.put("saved_account_" + Base64.getUrlEncoder().withoutPadding().encodeToString(id.getBytes(java.nio.charset.StandardCharsets.UTF_8)), token);
        }
        assertThatThrownBy(() -> service.add("sixth", "password", cookies, false))
                .isInstanceOfSatisfying(AuthBusinessException.class,
                        failure -> assertThat(failure.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_INVALID_REQUEST));
        verify(accounts, never()).findByUsername("sixth");
    }

    private static AuthAccountRecord account(String id, String role, boolean active, boolean banned) {
        return new AuthAccountRecord(id, id, id + "@example.test", "hash", role, active, banned, null, null);
    }
    private static AuthSession session(String id, String role) {
        return new AuthSession(LoginResponse.builder().csrfToken("csrf")
                .user(new AuthUserVO(id, id, id, id + "@example.test", role, true, false, "")).build(), List.of());
    }
}
