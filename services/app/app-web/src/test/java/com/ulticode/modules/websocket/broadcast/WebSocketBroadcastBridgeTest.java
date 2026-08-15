package com.ulticode.modules.websocket.broadcast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.app.api.dto.NotificationPayload;
import com.ulticode.modules.websocket.config.WebSocketProperties;
import com.ulticode.modules.websocket.event.ContestStatusEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketBroadcastBridge")
class WebSocketBroadcastBridgeTest {

  @Mock private SimpMessagingTemplate messagingTemplate;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ObjectProvider<StringRedisTemplate> redisTemplateProvider;

  private WebSocketProperties properties;
  private ObjectMapper objectMapper;
  private WebSocketBroadcastBridge bridge;

  @BeforeEach
  void setUp() {
    properties = new WebSocketProperties();
    objectMapper = new ObjectMapper().findAndRegisterModules();
    bridge = new WebSocketBroadcastBridge(messagingTemplate, properties, redisTemplateProvider, objectMapper);
  }

  @Test
  @DisplayName("Default broadcast flag is false -> calls local messagingTemplate directly")
  void send_defaultFlagFalse_callsLocalMessagingTemplate() {
    ContestStatusEvent event =
        new ContestStatusEvent("c-1", ContestStatusEvent.ContestStatus.RUNNING, null, null, null);
    bridge.send("/topic/contest/c-1/status", event);

    verify(messagingTemplate).convertAndSend("/topic/contest/c-1/status", event);
    verify(redisTemplateProvider, never()).getIfAvailable();
  }

  @Test
  @DisplayName("Broadcast flag enabled + Redis present -> publishes message to Redis Pub/Sub channel")
  void send_broadcastFlagEnabled_publishesToRedis() {
    properties.getBroadcast().setEnabled(true);
    when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);

    ContestStatusEvent event =
        new ContestStatusEvent("c-1", ContestStatusEvent.ContestStatus.RUNNING, null, null, null);
    bridge.send("/topic/contest/c-1/status", event);

    verify(redisTemplate).convertAndSend(eq("ulticode:ws:broadcast"), anyString());
    verify(messagingTemplate, never()).convertAndSend(anyString(), (Object) anyString());
  }

  @Test
  @DisplayName("User send with broadcast flag enabled + Redis present -> publishes user WS message to Redis")
  void sendToUser_broadcastFlagEnabled_publishesToRedis() {
    properties.getBroadcast().setEnabled(true);
    when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);

    NotificationPayload payload = NotificationPayload.system("n-1", "Title", "Body");
    bridge.sendToUser("u-1", "/queue/notification", payload);

    verify(redisTemplate).convertAndSend(eq("ulticode:ws:broadcast"), anyString());
    verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), eq(payload));
  }

  @Test
  @DisplayName("Broadcast flag enabled but Redis template null -> falls back to local messagingTemplate")
  void send_redisNull_fallsBackToLocal() {
    properties.getBroadcast().setEnabled(true);
    when(redisTemplateProvider.getIfAvailable()).thenReturn(null);

    assertThat(bridge.shouldBroadcast()).isFalse();

    bridge.send("/topic/test", "payload");
    verify(messagingTemplate).convertAndSend("/topic/test", "payload");
  }
}
