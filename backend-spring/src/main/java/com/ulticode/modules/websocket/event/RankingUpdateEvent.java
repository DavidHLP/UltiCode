package com.ulticode.modules.websocket.event;

import java.time.Instant;
import java.util.List;

/**
 * Ranking update event payload.
 *
 * <p>Sent to all clients subscribed to a contest room when rankings change.
 */
public record RankingUpdateEvent(
    String contestId, List<RankingItem> rankings, Instant updatedAt) {

  /** Individual ranking item. */
  public record RankingItem(
      int rank,
      String userId,
      String username,
      double score,
      int solvedCount,
      long penalty) {}
}
