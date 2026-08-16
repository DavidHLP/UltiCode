package com.ulticode.modules.websocket.broadcast;


/**
 * Message envelope serialized over Redis Pub/Sub for multi-instance STOMP WebSocket broadcasting.
 *
 * <p>{@code kind} is a closed-string discriminator resolved through {@link WebSocketPayloadKind};
 * the listener never reflects on an attacker-controlled class name.
 *
 * @author ulticode
 */
public class WebSocketBroadcastMessage {

  public enum Type {
    BROADCAST,
    USER
  }

  private Type type;
  private String destination;
  private String userId;
  private String payloadJson;
  private String kind;

  public WebSocketBroadcastMessage() {}

  public WebSocketBroadcastMessage(
      Type type, String destination, String userId, String payloadJson, String kind) {
    this.type = type;
    this.destination = destination;
    this.userId = userId;
    this.payloadJson = payloadJson;
    this.kind = kind;
  }

  public Type getType() {
    return type;
  }

  public void setType(Type type) {
    this.type = type;
  }

  public String getDestination() {
    return destination;
  }

  public void setDestination(String destination) {
    this.destination = destination;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getPayloadJson() {
    return payloadJson;
  }

  public void setPayloadJson(String payloadJson) {
    this.payloadJson = payloadJson;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }
}
