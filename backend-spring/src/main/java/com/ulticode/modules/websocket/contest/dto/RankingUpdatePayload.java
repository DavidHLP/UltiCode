package com.ulticode.modules.websocket.contest.dto;

import java.time.Instant;
import java.util.List;

/**
 * Ranking update payload for contest events.
 *
 * <p>Sent to all clients subscribed to a contest room when rankings change.
 */
public record RankingUpdatePayload(
    String event,
    String contestId,
    List<RankingItem> rankings,
    Instant updatedAt) {

  /** Individual ranking item. */
  public record RankingItem(
      int rank,
      String userId,
      String username,
      double score,
      int solvedCount,
      long penalty) {}

  /**
   * Create a ranking update payload.
   *
   * @param contestId the contest ID
   * @param rankings the list of ranking items
   * @return ranking update payload
   */
  public static RankingUpdatePayload of(String contestId, List<RankingItem> rankings) {
    return new RankingUpdatePayload(
        "ranking_update",
        contestId,
        rankings,
        Instant.now());
  }
}
