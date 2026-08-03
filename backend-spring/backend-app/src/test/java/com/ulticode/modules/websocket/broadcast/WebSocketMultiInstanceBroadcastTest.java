package com.ulticode.modules.websocket.broadcast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.websocket.config.WebSocketProperties;
import com.ulticode.modules.websocket.event.ContestStatusEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end unit simulation proving multi-instance WebSocket broadcast:
 * Instance A emits a WS status message via {@link WebSocketBroadcastBridge}.
 * The message is published to Redis Pub/Sub, which triggers {@link WebSocketBroadcastListener}
 * on Instance B, resulting in Instance B's local STOMP messaging template delivering the push.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketMultiInstanceBroadcastTest")
class WebSocketMultiInstanceBroadcastTest {

  @Mock private SimpMessagingTemplate instanceAMessagingTemplate;
  @Mock private SimpMessagingTemplate instanceBMessagingTemplate;
  @Mock private StringRedisTemplate sharedRedis;
  @Mock private ObjectProvider<StringRedisTemplate> redisProviderA;

  @Test
  @DisplayName("Multi-instance WS bridge: Instance A publish -> Redis -> Instance B listener relays to local STOMP")
  void multiInstanceBroadcast_relaysFromInstanceAToInstanceB() {
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    // Configure Instance A (producer) with broadcast enabled
    WebSocketProperties propsA = new WebSocketProperties();
    propsA.getBroadcast().setEnabled(true);
    when(redisProviderA.getIfAvailable()).thenReturn(sharedRedis);

    WebSocketBroadcastBridge bridgeInstanceA =
        new WebSocketBroadcastBridge(instanceAMessagingTemplate, propsA, redisProviderA, objectMapper);

    // Configure Instance B (consumer)
    WebSocketBroadcastListener listenerInstanceB =
        new WebSocketBroadcastListener(instanceBMessagingTemplate, objectMapper);

    // Wire simulated Redis Pub/Sub: when sharedRedis.convertAndSend is called, deliver byte payload to Instance B's listener
    doAnswer(
            invocation -> {
              String channel = invocation.getArgument(0);
              String messageJson = invocation.getArgument(1);
              DefaultMessage message =
                  new DefaultMessage(channel.getBytes(StandardCharsets.UTF_8), messageJson.getBytes(StandardCharsets.UTF_8));
              listenerInstanceB.onMessage(message, null);
              return null;
            })
        .when(sharedRedis)
        .convertAndSend(anyString(), anyString());

    // Instance A publishes contest status event
    ContestStatusEvent event =
        new ContestStatusEvent("contest-99", ContestStatusEvent.ContestStatus.RUNNING, null, null, "Contest live!");
    bridgeInstanceA.send("/topic/contest/contest-99/status", event);

    // Assert Instance A published to Redis Pub/Sub
    verify(sharedRedis).convertAndSend(eq("ulticode:ws:broadcast"), anyString());

    // Assert Instance B received the message via Redis Pub/Sub listener and called its local STOMP template
    ArgumentCaptor<Object> captorB = ArgumentCaptor.forClass(Object.class);
    verify(instanceBMessagingTemplate).convertAndSend(eq("/topic/contest/contest-99/status"), captorB.capture());

    assertThat(captorB.getValue()).isInstanceOf(ContestStatusEvent.class);
    ContestStatusEvent receivedOnB = (ContestStatusEvent) captorB.getValue();
    assertThat(receivedOnB.contestId()).isEqualTo("contest-99");
    assertThat(receivedOnB.status()).isEqualTo(ContestStatusEvent.ContestStatus.RUNNING);
    assertThat(receivedOnB.message()).isEqualTo("Contest live!");
  }
}
