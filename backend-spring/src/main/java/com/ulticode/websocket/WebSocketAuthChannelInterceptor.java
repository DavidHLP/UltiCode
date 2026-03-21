package com.ulticode.websocket;

import com.ulticode.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Channel interceptor for authenticating WebSocket connections using JWT.
 * Extracts the token from the STOMP CONNECT message headers and validates it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            // Extract token from Authorization header
            String authHeader = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);

            if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
                String token = authHeader.substring(BEARER_PREFIX.length());

                try {
                    if (jwtTokenProvider.validateToken(token)) {
                        String userId = jwtTokenProvider.getUserIdFromToken(token);
                        String role = jwtTokenProvider.getRoleFromToken(token);

                        // Create authentication principal
                        Authentication authentication = new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role))
                        );

                        accessor.setUser(authentication);
                        log.debug("WebSocket authenticated for user: {}", userId);
                    } else {
                        log.warn("Invalid JWT token for WebSocket connection");
                    }
                } catch (Exception e) {
                    log.error("Error authenticating WebSocket connection: {}", e.getMessage());
                }
            } else {
                log.warn("No valid Authorization header found in WebSocket CONNECT message");
            }
        }

        return message;
    }
}
