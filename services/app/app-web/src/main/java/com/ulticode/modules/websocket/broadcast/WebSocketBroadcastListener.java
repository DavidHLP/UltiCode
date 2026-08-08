package com.ulticode.modules.websocket.broadcast;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Redis Pub/Sub listener that receives WebSocket broadcast messages and relays them
 * to the local STOMP {@link SimpMessagingTemplate}.
 *
 * <p><strong>Security.</strong> The payload target class is resolved <em>only</em> through the
 * closed {@link WebSocketPayloadKind} allowlist. A message carrying a {@code kind} outside the
 * allowlist is dropped without any reflection or deserialization of the payload body, so a
 * publisher that gains write access to the broadcast channel cannot instantiate arbitrary
 * classpath classes via attacker-supplied JSON.
 *
 * @author ulticode
 */
@Component
public class WebSocketBroadcastListener implements MessageListener {

  private static final Logger log = LoggerFactory.getLogger(WebSocketBroadcastListener.class);

  private final SimpMessagingTemplate messagingTemplate;
  private final ObjectMapper objectMapper;

  public WebSocketBroadcastListener(
      SimpMessagingTemplate messagingTemplate,
      ObjectMapper objectMapper) {
    this.messagingTemplate = messagingTemplate;
    this.objectMapper = objectMapper;
  }

  @Override
  public void onMessage(Message message, byte[] pattern) {
    String destination = null;
    try {
      String json = new String(message.getBody(), StandardCharsets.UTF_8);
      WebSocketBroadcastMessage broadcastMsg = objectMapper.readValue(json, WebSocketBroadcastMessage.class);
      destination = broadcastMsg.getDestination();
      String kindWire = broadcastMsg.getKind();

      // Allowlist gate: never reflect on attacker-controlled strings. An unknown kind is a
      // poisoned/gadget message — drop it without deserializing the payload body.
      WebSocketPayloadKind kind = WebSocketPayloadKind.fromWire(kindWire);
      if (kind == null) {
        log.warn(
            "Dropping WS broadcast message with unknown payload kind '{}' (destination={}); "
                + "not in WebSocketPayloadKind allowlist.",
            kindWire, destination);
        return;
      }

      Object payload = objectMapper.readValue(broadcastMsg.getPayloadJson(), kind.payloadClass());

      if (broadcastMsg.getType() == WebSocketBroadcastMessage.Type.BROADCAST) {
        messagingTemplate.convertAndSend(broadcastMsg.getDestination(), payload);
      } else if (broadcastMsg.getType() == WebSocketBroadcastMessage.Type.USER) {
        messagingTemplate.convertAndSendToUser(
            broadcastMsg.getUserId(), broadcastMsg.getDestination(), payload);
      }
      log.debug("Relayed broadcast WS message to local STOMP: {}", broadcastMsg.getDestination());
    } catch (Exception e) {
      log.error("Error processing Redis WS broadcast message (destination={})", destination, e);
    }
  }
}
