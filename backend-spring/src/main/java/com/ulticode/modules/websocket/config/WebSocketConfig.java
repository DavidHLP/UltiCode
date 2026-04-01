package com.ulticode.modules.websocket.config;

import com.ulticode.modules.websocket.constants.WebSocketConstants;
import com.ulticode.modules.websocket.interceptor.WebSocketAuthHandshakeInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket configuration for STOMP messaging with SockJS fallback.
 *
 * <p>Enables real-time communication for contest updates, rankings, and notifications.
 * Provides two separate endpoints:
 * <ul>
 *   <li>/ws/contest - For contest-related real-time updates
 *   <li>/ws/notifications - For user notification delivery
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

  private final WebSocketProperties properties;
  private final WebSocketAuthHandshakeInterceptor authHandshakeInterceptor;

  public WebSocketConfig(
      WebSocketProperties properties,
      WebSocketAuthHandshakeInterceptor authHandshakeInterceptor) {
    this.properties = properties;
    this.authHandshakeInterceptor = authHandshakeInterceptor;
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    // Enable a simple memory-based message broker
    // Messages destined for /topic or /queue will be routed to the message broker
    config.enableSimpleBroker(WebSocketConstants.TOPIC_PREFIX, WebSocketConstants.QUEUE_PREFIX);

    // Set prefix for messages bound for @MessageMapping methods
    config.setApplicationDestinationPrefixes(WebSocketConstants.APP_PREFIX);

    // Set prefix for user-specific messages (e.g., /user/queue/...)
    config.setUserDestinationPrefix(WebSocketConstants.USER_DESTINATION_PREFIX);
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    // Register contest WebSocket endpoint with SockJS fallback
    registry
        .addEndpoint(WebSocketConstants.ENDPOINT_CONTEST)
        .setAllowedOriginPatterns(properties.getAllowedOrigins())
        .addInterceptors(authHandshakeInterceptor)
        .withSockJS();

    // Register notifications WebSocket endpoint with SockJS fallback
    registry
        .addEndpoint(WebSocketConstants.ENDPOINT_NOTIFICATIONS)
        .setAllowedOriginPatterns(properties.getAllowedOrigins())
        .addInterceptors(authHandshakeInterceptor)
        .withSockJS();

    // Also register a generic /ws endpoint for backward compatibility
    registry
        .addEndpoint("/ws")
        .setAllowedOriginPatterns(properties.getAllowedOrigins())
        .addInterceptors(authHandshakeInterceptor)
        .withSockJS();
  }
}
