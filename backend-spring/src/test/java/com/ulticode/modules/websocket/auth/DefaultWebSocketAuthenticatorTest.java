package com.ulticode.modules.websocket.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.auth.util.JwtUtils;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.projection.UserReadProjection;
import com.ulticode.modules.websocket.dto.SocketClientData;
import com.ulticode.modules.websocket.interceptor.JwtChannelInterceptor.WebSocketAuthenticationException;
import com.ulticode.modules.websocket.port.TokenBlacklistPort;
import io.jsonwebtoken.Claims;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
 * (Phase 0 / MICROSERVICE_MIGRATION_GUIDE.md §7.1). The fail-closed
 * Redis contract is regression-protected by the blacklist-error case.
 */
@ExtendWith(MockitoExtension.class)
class DefaultWebSocketAuthenticatorTest {

    private static final String TOKEN = "raw.jwt.token";
    private static final String USER_ID = "u-1";
    private static final Instant NOW = Instant.parse("2026-07-25T00:00:00Z");

    @Mock private TokenBlacklistPort tokenBlacklistPort;
    @Mock private JwtUtils jwtUtils;
    @Mock private UserReadProjection userReadProjection;

    private DefaultWebSocketAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        // Fixed Clock so banned_until comparisons are deterministic across runs.
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        authenticator = new DefaultWebSocketAuthenticator(
                tokenBlacklistPort, jwtUtils, userReadProjection, clock);
    }

    @Test
    @DisplayName("missing token → WEBSOCKET_UNAUTHORIZED")
    void missingToken() {
        assertThatThrownBy(() -> authenticator.authenticate(Optional.empty()))
                .isInstanceOf(WebSocketAuthenticationException.class)
                .extracting(e -> ((WebSocketAuthenticationException) e).getErrorCode())
                .isEqualTo(ErrorCode.WEBSOCKET_UNAUTHORIZED);
    }

    @Test
    @DisplayName("blacklisted token → WEBSOCKET_TOKEN_BLACKLISTED")
    void blacklistedToken() {
        when(tokenBlacklistPort.isBlacklisted(TOKEN)).thenReturn(true);

        assertThatThrownBy(() -> authenticator.authenticate(Optional.of(TOKEN)))
                .isInstanceOf(WebSocketAuthenticationException.class)
                .extracting(e -> ((WebSocketAuthenticationException) e).getErrorCode())
                .isEqualTo(ErrorCode.WEBSOCKET_TOKEN_BLACKLISTED);
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
        when(jwtUtils.validateToken(TOKEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticator.authenticate(Optional.of(TOKEN)))
                .isInstanceOf(WebSocketAuthenticationException.class)
                .extracting(e -> ((WebSocketAuthenticationException) e).getErrorCode())
                .isEqualTo(ErrorCode.WEBSOCKET_INVALID_TOKEN);
    }

    @Test
    @DisplayName("JWT with null subject → WEBSOCKET_INVALID_TOKEN")
    void jwtWithoutSubject_null() {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(tokenBlacklistPort.isBlacklisted(TOKEN)).thenReturn(false);
        when(jwtUtils.validateToken(TOKEN)).thenReturn(Optional.of(claims));
        when(claims.getSubject()).thenReturn(null);

        assertThatThrownBy(() -> authenticator.authenticate(Optional.of(TOKEN)))
                .isInstanceOf(WebSocketAuthenticationException.class)
                .extracting(e -> ((WebSocketAuthenticationException) e).getErrorCode())
                .isEqualTo(ErrorCode.WEBSOCKET_INVALID_TOKEN);
    }

    @Test
    @DisplayName("JWT with empty subject → WEBSOCKET_INVALID_TOKEN")
    void jwtWithoutSubject_empty() {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(tokenBlacklistPort.isBlacklisted(TOKEN)).thenReturn(false);
        when(jwtUtils.validateToken(TOKEN)).thenReturn(Optional.of(claims));
        when(claims.getSubject()).thenReturn("");

        assertThatThrownBy(() -> authenticator.authenticate(Optional.of(TOKEN)))
                .isInstanceOf(WebSocketAuthenticationException.class)
                .extracting(e -> ((WebSocketAuthenticationException) e).getErrorCode())
                .isEqualTo(ErrorCode.WEBSOCKET_INVALID_TOKEN);
    }

    @Test
    @DisplayName("user not found → WEBSOCKET_USER_NOT_FOUND")
    void userNotFound() {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(tokenBlacklistPort.isBlacklisted(TOKEN)).thenReturn(false);
        when(jwtUtils.validateToken(TOKEN)).thenReturn(Optional.of(claims));
        when(claims.getSubject()).thenReturn(USER_ID);
        when(userReadProjection.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticator.authenticate(Optional.of(TOKEN)))
                .isInstanceOf(WebSocketAuthenticationException.class)
                .extracting(e -> ((WebSocketAuthenticationException) e).getErrorCode())
                .isEqualTo(ErrorCode.WEBSOCKET_USER_NOT_FOUND);
    }

    @Test
    @DisplayName("happy path returns SocketClientData with copied identity")
    void happyPath() {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("alice");
        user.setRole("USER");
        user.setIsActive(true);
        user.setIsBanned(false);
        when(tokenBlacklistPort.isBlacklisted(TOKEN)).thenReturn(false);
        when(jwtUtils.validateToken(TOKEN)).thenReturn(Optional.of(claims));
        when(claims.getSubject()).thenReturn(USER_ID);
        when(userReadProjection.findById(USER_ID)).thenReturn(Optional.of(user));

        SocketClientData data = authenticator.authenticate(Optional.of(TOKEN));

        assertThat(data.userId()).isEqualTo(USER_ID);
        assertThat(data.username()).isEqualTo("alice");
        assertThat(data.role()).isEqualTo("USER");
    }

    // ============ Phase 0 §7.1: active/ban CONNECT gating ============

    @Test
    @DisplayName("inactive account → WEBSOCKET_USER_BANNED (no DB lookup shortcut)")
    void inactiveAccount_rejected() {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        User user = new User();
        user.setId(USER_ID);
        user.setIsActive(false);     // explicitly inactive
        user.setIsBanned(false);
        when(tokenBlacklistPort.isBlacklisted(TOKEN)).thenReturn(false);
        when(jwtUtils.validateToken(TOKEN)).thenReturn(Optional.of(claims));
        when(claims.getSubject()).thenReturn(USER_ID);
        when(userReadProjection.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authenticator.authenticate(Optional.of(TOKEN)))
                .isInstanceOf(WebSocketAuthenticationException.class)
                .extracting(e -> ((WebSocketAuthenticationException) e).getErrorCode())
                .isEqualTo(ErrorCode.WEBSOCKET_USER_BANNED);
    }

    @Test
    @DisplayName("banned account with future banned_until → WEBSOCKET_USER_BANNED")
    void bannedAccount_futureUntil_rejected() {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        User user = new User();
        user.setId(USER_ID);
        user.setIsActive(true);
        user.setIsBanned(true);
        user.setBannedUntil(LocalDateTime.of(2027, 1, 1, 0, 0)); // > NOW
        when(tokenBlacklistPort.isBlacklisted(TOKEN)).thenReturn(false);
        when(jwtUtils.validateToken(TOKEN)).thenReturn(Optional.of(claims));
        when(claims.getSubject()).thenReturn(USER_ID);
        when(userReadProjection.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authenticator.authenticate(Optional.of(TOKEN)))
                .isInstanceOf(WebSocketAuthenticationException.class)
                .extracting(e -> ((WebSocketAuthenticationException) e).getErrorCode())
                .isEqualTo(ErrorCode.WEBSOCKET_USER_BANNED);
    }

    @Test
    @DisplayName("banned account with past banned_until → CONNECT allowed (expire backstop)")
    void bannedAccount_pastUntil_allowed() {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("alice");
        user.setRole("USER");
        user.setIsActive(true);
        user.setIsBanned(true);
        user.setBannedUntil(LocalDateTime.of(2025, 1, 1, 0, 0)); // < NOW
        when(tokenBlacklistPort.isBlacklisted(TOKEN)).thenReturn(false);
        when(jwtUtils.validateToken(TOKEN)).thenReturn(Optional.of(claims));
        when(claims.getSubject()).thenReturn(USER_ID);
        when(userReadProjection.findById(USER_ID)).thenReturn(Optional.of(user));

        SocketClientData data = authenticator.authenticate(Optional.of(TOKEN));

        assertThat(data.userId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("banned account with no banned_until → WEBSOCKET_USER_BANNED")
    void bannedAccount_noUntil_rejected() {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        User user = new User();
        user.setId(USER_ID);
        user.setIsActive(true);
        user.setIsBanned(true);
        user.setBannedUntil(null); // permanent ban
        when(tokenBlacklistPort.isBlacklisted(TOKEN)).thenReturn(false);
        when(jwtUtils.validateToken(TOKEN)).thenReturn(Optional.of(claims));
        when(claims.getSubject()).thenReturn(USER_ID);
        when(userReadProjection.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authenticator.authenticate(Optional.of(TOKEN)))
                .isInstanceOf(WebSocketAuthenticationException.class)
                .extracting(e -> ((WebSocketAuthenticationException) e).getErrorCode())
                .isEqualTo(ErrorCode.WEBSOCKET_USER_BANNED);
    }
}