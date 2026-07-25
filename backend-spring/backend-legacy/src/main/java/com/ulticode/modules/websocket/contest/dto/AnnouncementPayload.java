package com.ulticode.modules.websocket.contest.dto;

import java.time.Instant;

/**
 * Announcement payload for contest events.
 *
 * <p>Sent when a new contest announcement is created.
 */
public record AnnouncementPayload(
    String event,
    String id,
    String contestId,
    String title,
    String content,
    Instant createdAt) {

  /**
   * Create an announcement payload.
   *
   * @param id the announcement ID
   * @param contestId the contest ID
   * @param title the announcement title
   * @param content the announcement content
   * @return announcement payload
   */
  public static AnnouncementPayload of(
      String id,
      String contestId,
      String title,
      String content) {
    return new AnnouncementPayload(
        "announcement",
        id,
        contestId,
        title,
        content,
        Instant.now());
  }
}
