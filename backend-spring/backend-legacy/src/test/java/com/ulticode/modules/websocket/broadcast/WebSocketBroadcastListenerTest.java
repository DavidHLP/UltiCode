package com.ulticode.modules.websocket.broadcast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.websocket.event.ContestStatusEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketBroadcastListener")
class WebSocketBroadcastListenerTest {

  @Mock private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

  private ObjectMapper objectMapper;
  private WebSocketBroadcastListener listener;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper().findAndRegisterModules();
    listener = new WebSocketBroadcastListener(messagingTemplate, objectMapper);
  }

  @Test
  @DisplayName("onMessage deserializes BROADCAST message and relays to local STOMP convertAndSend")
  void onMessage_relaysBroadcastMessage() throws Exception {
    ContestStatusEvent payload =
        new ContestStatusEvent("c-100", ContestStatusEvent.ContestStatus.RUNNING, null, null, "Go!");
    String payloadJson = objectMapper.writeValueAsString(payload);
    WebSocketBroadcastMessage msg =
        new WebSocketBroadcastMessage(
            WebSocketBroadcastMessage.Type.BROADCAST,
            "/topic/contest/c-100/status",
            null,
            payloadJson,
            ContestStatusEvent.class.getName());
    String msgJson = objectMapper.writeValueAsString(msg);

    DefaultMessage redisMsg = new DefaultMessage("ulticode:ws:broadcast".getBytes(), msgJson.getBytes(StandardCharsets.UTF_8));
    listener.onMessage(redisMsg, null);

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(messagingTemplate).convertAndSend(eq("/topic/contest/c-100/status"), captor.capture());

    assertThat(captor.getValue()).isInstanceOf(ContestStatusEvent.class);
    ContestStatusEvent received = (ContestStatusEvent) captor.getValue();
    assertThat(received.contestId()).isEqualTo("c-100");
    assertThat(received.status()).isEqualTo(ContestStatusEvent.ContestStatus.RUNNING);
  }

  @Test
  @DisplayName("onMessage deserializes USER message and relays to local STOMP convertAndSendToUser")
  void onMessage_relaysUserMessage() throws Exception {
    WebSocketBroadcastMessage msg =
        new WebSocketBroadcastMessage(
            WebSocketBroadcastMessage.Type.USER,
            "/queue/notification",
            "u-99",
            "\"Test notification\"",
            String.class.getName());
    String msgJson = objectMapper.writeValueAsString(msg);

    DefaultMessage redisMsg = new DefaultMessage("ulticode:ws:broadcast".getBytes(), msgJson.getBytes(StandardCharsets.UTF_8));
    listener.onMessage(redisMsg, null);

    verify(messagingTemplate).convertAndSendToUser("u-99", "/queue/notification", "Test notification");
  }
}
