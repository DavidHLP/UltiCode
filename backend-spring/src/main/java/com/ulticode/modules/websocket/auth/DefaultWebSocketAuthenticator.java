package com.ulticode.modules.websocket.auth;

import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.auth.util.JwtUtils;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.projection.UserReadProjection;
import com.ulticode.modules.websocket.dto.SocketClientData;
import com.ulticode.modules.websocket.interceptor.JwtChannelInterceptor.WebSocketAuthenticationException;
import com.ulticode.modules.websocket.port.TokenBlacklistPort;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
 * </ol>
 *
 * @author ulticode
 */
@Slf4j
@Component
public class DefaultWebSocketAuthenticator implements WebSocketAuthenticator {

    private final TokenBlacklistPort tokenBlacklistPort;
    private final JwtUtils jwtUtils;
    private final UserReadProjection userReadProjection;

    public DefaultWebSocketAuthenticator(TokenBlacklistPort tokenBlacklistPort,
                                        JwtUtils jwtUtils,
                                        UserReadProjection userReadProjection) {
        this.tokenBlacklistPort = tokenBlacklistPort;
        this.jwtUtils = jwtUtils;
        this.userReadProjection = userReadProjection;
    }

    @Override
    public SocketClientData authenticate(Optional<String> tokenOpt) {
        if (tokenOpt.isEmpty()) {
            log.warn("WebSocket connection rejected: No token provided");
            throw new WebSocketAuthenticationException(
                    ErrorCode.WEBSOCKET_UNAUTHORIZED, "No authentication token provided");
        }
        String token = tokenOpt.get();

        // Fail-closed: Redis errors here propagate and the caller treats the
        // CONNECT as failed. See TokenBlacklistPort contract.
        if (tokenBlacklistPort.isBlacklisted(token)) {
            log.warn("WebSocket connection rejected: Token is blacklisted");
            throw new WebSocketAuthenticationException(
                    ErrorCode.WEBSOCKET_TOKEN_BLACKLISTED, "Token has been revoked");
        }

        Optional<Claims> claimsOpt = validateToken(token);
        if (claimsOpt.isEmpty()) {
            log.warn("WebSocket connection rejected: Invalid token");
            throw new WebSocketAuthenticationException(
                    ErrorCode.WEBSOCKET_INVALID_TOKEN, "Invalid or expired token");
        }

        String userId = claimsOpt.get().getSubject();
        if (userId == null || userId.isEmpty()) {
            log.warn("WebSocket connection rejected: Invalid token payload");
            throw new WebSocketAuthenticationException(
                    ErrorCode.WEBSOCKET_INVALID_TOKEN, "Invalid token payload");
        }

        Optional<User> userOpt = userReadProjection.findById(userId);
        if (userOpt.isEmpty()) {
            log.warn("WebSocket connection rejected: User not found, userId: {}", userId);
            throw new WebSocketAuthenticationException(
                    ErrorCode.WEBSOCKET_USER_NOT_FOUND, "User not found");
        }

        User user = userOpt.get();
        return new SocketClientData(userId, user.getUsername(), user.getRole());
    }

    @Override
    public Optional<Claims> validateToken(String token) {
        return jwtUtils.validateToken(token);
    }
}