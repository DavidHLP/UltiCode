package com.ulticode.modules.websocket.dto;

/** Client data stored in WebSocket session attributes. */
public record SocketClientData(String userId, String username, String role) {}
