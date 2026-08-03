package com.ulticode.modules.websocket.util;

import java.util.UUID;

/** Utility class for WebSocket operations. */
public final class WebSocketUtils {

  private WebSocketUtils() {
    // Utility class
  }

  /**
   * Validate contest ID format (UUID).
   *
   * @param contestId the contest ID to validate
   * @return true if valid UUID format
   */
  public static boolean isValidContestId(String contestId) {
    if (contestId == null || contestId.isEmpty()) {
      return false;
    }
    try {
      UUID.fromString(contestId);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  /**
   * Get contest room name from contest ID.
   *
   * @param contestId the contest ID
   * @return the room name
   */
  public static String getContestRoomName(String contestId) {
    return "/topic/contest/" + contestId;
  }

  /**
   * Get user-specific queue name.
   *
   * @param userId the user ID
   * @return the queue name
   */
  public static String getUserQueueName(String userId) {
    return "/user/" + userId + "/queue";
  }
}
