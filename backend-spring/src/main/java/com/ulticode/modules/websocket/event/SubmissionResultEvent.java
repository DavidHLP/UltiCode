package com.ulticode.modules.websocket.event;

import java.time.Instant;

/**
 * Submission result event payload.
 *
 * <p>Sent to individual users when their submission is judged.
 */
public record SubmissionResultEvent(
    String submissionId,
    String contestId,
    String problemId,
    String userId,
    String status,
    double score,
    Integer timeUsed,
    Long memoryUsed,
    Instant judgedAt) {}
