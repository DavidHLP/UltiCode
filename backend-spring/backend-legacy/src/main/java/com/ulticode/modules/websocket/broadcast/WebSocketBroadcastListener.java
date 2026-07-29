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
    try {
      String json = new String(message.getBody(), StandardCharsets.UTF_8);
      WebSocketBroadcastMessage broadcastMsg = objectMapper.readValue(json, WebSocketBroadcastMessage.class);
      Class<?> payloadClass = Class.forName(broadcastMsg.getPayloadClass());
      Object payload = objectMapper.readValue(broadcastMsg.getPayloadJson(), payloadClass);

      if (broadcastMsg.getType() == WebSocketBroadcastMessage.Type.BROADCAST) {
        messagingTemplate.convertAndSend(broadcastMsg.getDestination(), payload);
      } else if (broadcastMsg.getType() == WebSocketBroadcastMessage.Type.USER) {
        messagingTemplate.convertAndSendToUser(
            broadcastMsg.getUserId(), broadcastMsg.getDestination(), payload);
      }
      log.debug("Relayed broadcast WS message to local STOMP: {}", broadcastMsg.getDestination());
    } catch (Exception e) {
      log.error("Error processing Redis WS broadcast message", e);
    }
  }
}
