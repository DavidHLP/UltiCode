package com.ulticode.submission.api.dto;

import java.time.Instant;
import java.io.Serializable;

/**
 * Submission result payload for contest events.
 *
 * <p>Sent to individual users when their submission is judged.
 * Extracted from websocket.contest.dto for P7-INFRA-S2: queue depends on
 * this type but websocket family has not yet relocated.
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
    Instant judgedAt) implements Serializable {
    private static final long serialVersionUID = 1L;


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
