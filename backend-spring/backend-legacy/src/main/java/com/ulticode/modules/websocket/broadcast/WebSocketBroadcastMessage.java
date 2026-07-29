package com.ulticode.modules.websocket.broadcast;

import java.io.Serializable;

/**
 * Message envelope serialized over Redis Pub/Sub for multi-instance STOMP WebSocket broadcasting.
 *
 * @author ulticode
 */
public class WebSocketBroadcastMessage implements Serializable {

  public enum Type {
    BROADCAST,
    USER
  }

  private Type type;
  private String destination;
  private String userId;
  private String payloadJson;
  private String payloadClass;

  public WebSocketBroadcastMessage() {}

  public WebSocketBroadcastMessage(
      Type type, String destination, String userId, String payloadJson, String payloadClass) {
    this.type = type;
    this.destination = destination;
    this.userId = userId;
    this.payloadJson = payloadJson;
    this.payloadClass = payloadClass;
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

  public String getPayloadClass() {
    return payloadClass;
  }

  public void setPayloadClass(String payloadClass) {
    this.payloadClass = payloadClass;
  }
}
