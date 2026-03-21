package com.ulticode.modules.websocket.contest.dto;

import java.time.Instant;

/**
 * Submission result payload for contest events.
 *
 * <p>Sent to individual users when their submission is judged.
 */
public record SubmissionResultPayload(
    String event,
    String submissionId,
    String contestId,
    String problemId,
    String userId,
    String status,
    double score,
    Integer timeUsed,
    Long memoryUsed,
    Instant judgedAt) {

  /**
   * Create a submission result payload.
   *
   * @param submissionId the submission ID
   * @param contestId the contest ID
   * @param problemId the problem ID
   * @param userId the user ID
   * @param status the submission status
   * @param score the score
   * @param timeUsed the time used in milliseconds
   * @param memoryUsed the memory used in bytes
   * @return submission result payload
   */
  public static SubmissionResultPayload of(
      String submissionId,
      String contestId,
      String problemId,
      String userId,
      String status,
      double score,
      Integer timeUsed,
      Long memoryUsed) {
    return new SubmissionResultPayload(
        "submission_result",
        submissionId,
        contestId,
        problemId,
        userId,
        status,
        score,
        timeUsed,
        memoryUsed,
        Instant.now());
  }
}
