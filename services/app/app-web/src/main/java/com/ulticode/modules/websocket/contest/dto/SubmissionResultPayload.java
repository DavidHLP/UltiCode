package com.ulticode.modules.websocket.contest.dto;

import java.time.Instant;

/**
 * Submission result payload for contest events.
 *
 * @deprecated Use {@link com.ulticode.submission.api.dto.SubmissionResultPayload} instead.
 * This class remains only until the websocket family relocates
 * (P7-RELOCATE-WEBSOCKET-001).
 */
@Deprecated(forRemoval = true)
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
