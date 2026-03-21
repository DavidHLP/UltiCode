package com.ulticode.modules.websocket.contest.dto;

import java.time.Instant;

/**
 * First solve notification payload.
 *
 * <p>Sent when a user is the first to solve a problem in a contest.
 */
public record FirstSolvePayload(
    String event,
    String contestId,
    String problemId,
    String problemTitle,
    String userId,
    String username,
    Instant solvedAt) {

  /**
   * Create a first solve payload.
   *
   * @param contestId the contest ID
   * @param problemId the problem ID
   * @param problemTitle the problem title
   * @param userId the user ID who solved it first
   * @param username the username who solved it first
   * @return first solve payload
   */
  public static FirstSolvePayload of(
      String contestId,
      String problemId,
      String problemTitle,
      String userId,
      String username) {
    return new FirstSolvePayload(
        "first_solve",
        contestId,
        problemId,
        problemTitle,
        userId,
        username,
        Instant.now());
  }
}
