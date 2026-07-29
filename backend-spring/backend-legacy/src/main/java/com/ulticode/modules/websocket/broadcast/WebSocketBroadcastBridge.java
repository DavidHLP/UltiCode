package com.ulticode.modules.websocket.broadcast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.websocket.config.WebSocketProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Multi-instance STOMP WebSocket broadcast bridge.
 *
 * <p>When feature flag {@code app.websocket.broadcast.enabled} is false (default), messages are
 * sent directly to the local {@link SimpMessagingTemplate}.
 *
 * <p>When true and {@link StringRedisTemplate} is present, messages are published to Redis Pub/Sub
 * so that all App instances relay the message to their local STOMP subscribers.
 *
 * @author ulticode
 */
@Component
public class WebSocketBroadcastBridge {

  private static final Logger log = LoggerFactory.getLogger(WebSocketBroadcastBridge.class);

  private final SimpMessagingTemplate messagingTemplate;
  private final WebSocketProperties properties;
  private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
  private final ObjectMapper objectMapper;

  public WebSocketBroadcastBridge(
      SimpMessagingTemplate messagingTemplate,
      WebSocketProperties properties,
      ObjectProvider<StringRedisTemplate> redisTemplateProvider,
      ObjectMapper objectMapper) {
    this.messagingTemplate = messagingTemplate;
    this.properties = properties;
    this.redisTemplateProvider = redisTemplateProvider;
    this.objectMapper = objectMapper;
  }

  /**
   * Send a STOMP broadcast message to a destination.
   *
   * @param destination destination path (e.g. /topic/contest/{id}/status)
   * @param payload payload object
   */
  public void send(String destination, Object payload) {
    if (shouldBroadcast()) {
      publishToRedis(WebSocketBroadcastMessage.Type.BROADCAST, destination, null, payload);
    } else {
      messagingTemplate.convertAndSend(destination, payload);
    }
  }

  /**
   * Send a STOMP user-targeted message.
   *
   * @param userId user ID
   * @param destination user queue destination
   * @param payload payload object
   */
  public void sendToUser(String userId, String destination, Object payload) {
    if (shouldBroadcast()) {
      publishToRedis(WebSocketBroadcastMessage.Type.USER, destination, userId, payload);
    } else {
      messagingTemplate.convertAndSendToUser(userId, destination, payload);
    }
  }

  /**
   * Directly send to local STOMP messaging template without broadcasting.
   */
  public void sendLocal(String destination, Object payload) {
    messagingTemplate.convertAndSend(destination, payload);
  }

  /**
   * Directly send user-targeted message to local STOMP messaging template without broadcasting.
   */
  public void sendToUserLocal(String userId, String destination, Object payload) {
    messagingTemplate.convertAndSendToUser(userId, destination, payload);
  }

  public boolean shouldBroadcast() {
    if (properties.getBroadcast() == null || !properties.getBroadcast().isEnabled()) {
      return false;
    }
    StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
    if (redis == null) {
      log.warn("WS broadcast enabled but StringRedisTemplate unavailable; falling back to local push.");
      return false;
    }
    return true;
  }

  private void publishToRedis(
      WebSocketBroadcastMessage.Type type, String destination, String userId, Object payload) {
    try {
      String payloadJson = objectMapper.writeValueAsString(payload);
      String payloadClass = payload.getClass().getName();
      WebSocketBroadcastMessage message =
          new WebSocketBroadcastMessage(type, destination, userId, payloadJson, payloadClass);
      String messageJson = objectMapper.writeValueAsString(message);
      String channel = properties.getBroadcast().getChannel();
      redisTemplateProvider.getIfAvailable().convertAndSend(channel, messageJson);
      log.debug("Published WS broadcast message to Redis channel {}: destination={}", channel, destination);
    } catch (Exception e) {
      log.error("Failed to publish WS message to Redis broadcast channel, falling back to local push", e);
      if (type == WebSocketBroadcastMessage.Type.BROADCAST) {
        messagingTemplate.convertAndSend(destination, payload);
      } else {
        messagingTemplate.convertAndSendToUser(userId, destination, payload);
      }
    }
  }
}
