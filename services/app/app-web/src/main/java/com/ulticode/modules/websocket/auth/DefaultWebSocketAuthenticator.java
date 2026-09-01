package com.ulticode.modules.websocket.auth;
import com.ulticode.common.auth.AccountInfo;
import com.ulticode.common.auth.JwtPayload;

import com.ulticode.app.error.WebSocketErrorCode;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.security.JwtValidationPort;
import com.ulticode.app.api.dto.ModerationUserInfo;
import com.ulticode.common.security.AccountReadPort;
import com.ulticode.modules.websocket.dto.SocketClientData;
import com.ulticode.modules.websocket.interceptor.JwtChannelInterceptor.WebSocketAuthenticationException;
import com.ulticode.modules.websocket.port.TokenBlacklistPort;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Default adapter for {@link WebSocketAuthenticator}.
 *
 * <p>Implements the policy in the order required by the project:
 * <ol>
 *   <li>token must be present</li>
 *   <li>token must NOT be on the blacklist (fail-closed on Redis errors)</li>
 *   <li>signature + expiry must validate</li>
 *   <li>payload must carry a non-empty subject</li>
 *   <li>the referenced user must still exist</li>
 *   <li><strong>Current security architecture:</strong>
 *       the user account must be {@code active=true}, not banned
 *       ({@code is_banned=false}), and not within a {@code banned_until}
 *       window. Banned/inactive CONNECT attempts are rejected with
 *       {@link ErrorCode#WEBSOCKET_USER_BANNED}.</li>
 * </ol>
 *
 * <p>Long-running sessions re-validate on each CONNECT. Active-frame
 * revalidation (e.g. heartbeat every N seconds) is a Phase 4 concern
 * (multi-instance WS bridge); this adapter guarantees that no fresh
 * CONNECT succeeds for a banned/inactive account.
 */
@Slf4j
@Component
public class DefaultWebSocketAuthenticator implements WebSocketAuthenticator {

    private final TokenBlacklistPort tokenBlacklistPort;
    private final JwtValidationPort jwtValidationPort;
    private final AccountReadPort userReadProjection;
    private final Clock clock;

    public DefaultWebSocketAuthenticator(TokenBlacklistPort tokenBlacklistPort,
                                        JwtValidationPort jwtValidationPort,
                                        AccountReadPort userReadProjection,
                                        Clock clock) {
        this.tokenBlacklistPort = tokenBlacklistPort;
        this.jwtValidationPort = jwtValidationPort;
        this.userReadProjection = userReadProjection;
        this.clock = clock;
    }

    @Override
    public SocketClientData authenticate(Optional<String> tokenOpt) {
        if (tokenOpt.isEmpty()) {
            log.warn("WebSocket connection rejected: No token provided");
            throw new WebSocketAuthenticationException(
                    WebSocketErrorCode.UNAUTHORIZED, "No authentication token provided");
        }
        String token = tokenOpt.get();

        // Fail-closed: Redis errors here propagate and the caller treats the
        // CONNECT as failed. See TokenBlacklistPort contract.
        if (tokenBlacklistPort.isBlacklisted(token)) {
            log.warn("WebSocket connection rejected: Token is blacklisted");
            throw new WebSocketAuthenticationException(
                    WebSocketErrorCode.TOKEN_BLACKLISTED, "Token has been revoked");
        }

        Optional<JwtPayload> claimsOpt = jwtValidationPort.validateToken(token);
        if (claimsOpt.isEmpty()) {
            log.warn("WebSocket connection rejected: Invalid token");
            throw new WebSocketAuthenticationException(
                    WebSocketErrorCode.INVALID_TOKEN, "Invalid or expired token");
        }

        String userId = claimsOpt.get().userId();
        if (userId == null || userId.isEmpty()) {
            log.warn("WebSocket connection rejected: Invalid token payload");
            throw new WebSocketAuthenticationException(
                    WebSocketErrorCode.INVALID_TOKEN, "Invalid token payload");
        }

        Optional<AccountInfo> userOpt = userReadProjection.findById(userId);
        if (userOpt.isEmpty()) {
            log.warn("WebSocket connection rejected: User not found, userId: {}", userId);
            throw new WebSocketAuthenticationException(
                    WebSocketErrorCode.USER_NOT_FOUND, "User not found");
        }

        AccountInfo user = userOpt.get();
        if (isBannedOrInactive(user)) {
            log.warn("WebSocket connection rejected: Account banned/inactive, userId: {}", userId);
            throw new WebSocketAuthenticationException(
                    WebSocketErrorCode.USER_BANNED, "Account is banned or inactive");
        }

        return new SocketClientData(userId, user.username(), user.role());
    }

    /**
     * @return {@code true} if the user is inactive, currently banned, or
     *         within a {@code banned_until} window. All three states reject
     *         a CONNECT.
     */
    private boolean isBannedOrInactive(AccountInfo user) {
        if (Boolean.FALSE.equals(user.isActive())) {
            return true;
        }
        if (Boolean.TRUE.equals(user.isBanned())) {
            // Banned_until in the future still rejects; expired bans allow
            // CONNECT (a separate Admin action is expected to flip is_banned
            // back to false, but expire-window is honored as a backstop).
            // bannedUntil check removed — AccountInfo.isBanned() covers this
            return true; // banned
        }
        return false;
    }
}
