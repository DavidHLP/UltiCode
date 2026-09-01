package com.ulticode.modules.websocket.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ulticode.common.auth.AccountInfo;
import com.ulticode.common.auth.JwtPayload;
import com.ulticode.common.security.AccountReadPort;
import com.ulticode.common.security.JwtValidationPort;
import com.ulticode.app.error.WebSocketErrorCode;
import com.ulticode.modules.websocket.dto.SocketClientData;
import com.ulticode.modules.websocket.interceptor.JwtChannelInterceptor.WebSocketAuthenticationException;
import com.ulticode.modules.websocket.port.TokenBlacklistPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;

/**
 * Tests for {@link DefaultWebSocketAuthenticator}.
 *
 * <p>Verifies the policy the transport now delegates to: presence → blacklist
 * → signature/expiry → payload sanity → user existence → active/ban
 * (see {@code docs/architecture/security.md}). The fail-closed Redis contract
 * is regression-protected by the blacklist-error case.
 */
@ExtendWith(MockitoExtension.class)
class DefaultWebSocketAuthenticatorTest {

    private static final String TOKEN = "raw.jwt.token";
    private static final String USER_ID = "u-1";
    private static final Instant NOW = Instant.parse("2026-07-25T00:00:00Z");

    @Mock private TokenBlacklistPort tokenBlacklistPort;
    @Mock private JwtValidationPort jwtValidationPort;
    @Mock private AccountReadPort accountReadPort;

    private DefaultWebSocketAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        // Fixed Clock so banned_until comparisons are deterministic across runs.
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        authenticator = new DefaultWebSocketAuthenticator(
                tokenBlacklistPort, jwtValidationPort, accountReadPort, clock);
    }

    @Test
    @DisplayName("missing token → WEBSOCKET_UNAUTHORIZED")
    void missingToken() {
        assertThatThrownBy(() -> authenticator.authenticate(Optional.empty()))
                .isInstanceOf(WebSocketAuthenticationException.class)
                .extracting(e -> ((WebSocketAuthenticationException) e).getErrorCode())
                .isEqualTo(WebSocketErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("blacklisted token → WEBSOCKET_TOKEN_BLACKLISTED")
    void blacklistedToken() {
        when(tokenBlacklistPort.isBlacklisted(TOKEN)).thenReturn(true);

        assertThatThrownBy(() -> authenticator.authenticate(Optional.of(TOKEN)))
                .isInstanceOf(WebSocketAuthenticationException.class)
                .extracting(e -> ((WebSocketAuthenticationException) e).getErrorCode())
                .isEqualTo(WebSocketErrorCode.TOKEN_BLACKLISTED);
    }

    @Test
    @DisplayName("Redis failure propagates (fail-closed); does NOT silently admit token")
    void blacklistStorageFailure_propagates() {
        when(tokenBlacklistPort.isBlacklisted(TOKEN))
                .thenThrow(new QueryTimeoutException("Redis down"));

        assertThatThrownBy(() -> authenticator.authenticate(Optional.of(TOKEN)))
                .isInstanceOf(QueryTimeoutException.class);
    }

    @Test
    @DisplayName("invalid/expired JWT → WEBSOCKET_INVALID_TOKEN")
    void invalidJwt() {
        when(tokenBlacklistPort.isBlacklisted(TOKEN)).thenReturn(false);
        when(jwtValidationPort.validateToken(TOKEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticator.authenticate(Optional.of(TOKEN)))
                .isInstanceOf(WebSocketAuthenticationException.class)
                .extracting(e -> ((WebSocketAuthenticationException) e).getErrorCode())
                .isEqualTo(WebSocketErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("JWT with null userId → WEBSOCKET_INVALID_TOKEN")
    void jwtWithoutUserId_null() {
        JwtPayload jwtPayload = new JwtPayload(null, "alice", "USER");
        when(tokenBlacklistPort.isBlacklisted(TOKEN)).thenReturn(false);
        when(jwtValidationPort.validateToken(TOKEN)).thenReturn(Optional.of(jwtPayload));

        assertThatThrownBy(() -> authenticator.authenticate(Optional.of(TOKEN)))
                .isInstanceOf(WebSocketAuthenticationException.class)
                .extracting(e -> ((WebSocketAuthenticationException) e).getErrorCode())
                .isEqualTo(WebSocketErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("JWT with empty userId → WEBSOCKET_INVALID_TOKEN")
    void jwtWithoutUserId_empty() {
        JwtPayload jwtPayload = new JwtPayload("", "alice", "USER");
        when(tokenBlacklistPort.isBlacklisted(TOKEN)).thenReturn(false);
        when(jwtValidationPort.validateToken(TOKEN)).thenReturn(Optional.of(jwtPayload));

        assertThatThrownBy(() -> authenticator.authenticate(Optional.of(TOKEN)))
                .isInstanceOf(WebSocketAuthenticationException.class)
                .extracting(e -> ((WebSocketAuthenticationException) e).getErrorCode())
                .isEqualTo(WebSocketErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("user not found → WEBSOCKET_USER_NOT_FOUND")
    void userNotFound() {
        JwtPayload jwtPayload = new JwtPayload(USER_ID, "alice", "USER");
        when(tokenBlacklistPort.isBlacklisted(TOKEN)).thenReturn(false);
        when(jwtValidationPort.validateToken(TOKEN)).thenReturn(Optional.of(jwtPayload));
        when(accountReadPort.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticator.authenticate(Optional.of(TOKEN)))
                .isInstanceOf(WebSocketAuthenticationException.class)
                .extracting(e -> ((WebSocketAuthenticationException) e).getErrorCode())
                .isEqualTo(WebSocketErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("happy path returns SocketClientData with copied identity")
    void happyPath() {
        JwtPayload jwtPayload = new JwtPayload(USER_ID, "alice", "USER");
        AccountInfo accountInfo = new AccountInfo(USER_ID, "alice", "USER", true, false);
        when(tokenBlacklistPort.isBlacklisted(TOKEN)).thenReturn(false);
        when(jwtValidationPort.validateToken(TOKEN)).thenReturn(Optional.of(jwtPayload));
        when(accountReadPort.findById(USER_ID)).thenReturn(Optional.of(accountInfo));

        SocketClientData data = authenticator.authenticate(Optional.of(TOKEN));

        assertThat(data.userId()).isEqualTo(USER_ID);
        assertThat(data.username()).isEqualTo("alice");
        assertThat(data.role()).isEqualTo("USER");
    }

    // ============ Phase 0 §7.1: active/ban CONNECT gating ============

    @Test
    @DisplayName("inactive account → WEBSOCKET_USER_BANNED (no DB lookup shortcut)")
    void inactiveAccount_rejected() {
        JwtPayload jwtPayload = new JwtPayload(USER_ID, "alice", "USER");
        AccountInfo accountInfo = new AccountInfo(USER_ID, "alice", "USER", false, false);
        when(tokenBlacklistPort.isBlacklisted(TOKEN)).thenReturn(false);
        when(jwtValidationPort.validateToken(TOKEN)).thenReturn(Optional.of(jwtPayload));
        when(accountReadPort.findById(USER_ID)).thenReturn(Optional.of(accountInfo));

        assertThatThrownBy(() -> authenticator.authenticate(Optional.of(TOKEN)))
                .isInstanceOf(WebSocketAuthenticationException.class)
                .extracting(e -> ((WebSocketAuthenticationException) e).getErrorCode())
                .isEqualTo(WebSocketErrorCode.USER_BANNED);
    }

    @Test
    @DisplayName("banned account → WEBSOCKET_USER_BANNED")
    void bannedAccount_rejected() {
        JwtPayload jwtPayload = new JwtPayload(USER_ID, "alice", "USER");
        AccountInfo accountInfo = new AccountInfo(USER_ID, "alice", "USER", true, true);
        when(tokenBlacklistPort.isBlacklisted(TOKEN)).thenReturn(false);
        when(jwtValidationPort.validateToken(TOKEN)).thenReturn(Optional.of(jwtPayload));
        when(accountReadPort.findById(USER_ID)).thenReturn(Optional.of(accountInfo));

        assertThatThrownBy(() -> authenticator.authenticate(Optional.of(TOKEN)))
                .isInstanceOf(WebSocketAuthenticationException.class)
                .extracting(e -> ((WebSocketAuthenticationException) e).getErrorCode())
                .isEqualTo(WebSocketErrorCode.USER_BANNED);
    }
}
