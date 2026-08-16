package com.ulticode.app.api.dto;

import java.time.Instant;
import java.io.Serializable;

/**
 * Contest announcement payload for WebSocket broadcast.
 * Extracted from websocket.contest.dto for cross-module port access.
 */
public record AnnouncementPayload(
    String event,
    String contestId,
    String announcementId,
    String title,
    String content,
    String authorId,
    Instant createdAt) implements Serializable {
    private static final long serialVersionUID = 1L;


  public static AnnouncementPayload of(String contestId, String announcementId,
      String title, String content, String authorId) {
    return new AnnouncementPayload("contest_announcement", contestId, announcementId,
        title, content, authorId, Instant.now());
  }
}
