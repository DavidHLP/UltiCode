package com.ulticode.modules.submission.event;

import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * Event published when a submission's verdict has been finalized by the judge worker.
 *
 * <p>Consumed by multiple listeners at different transaction phases:
 * <ul>
 *   <li>{@code BEFORE_COMMIT}: {@code SubmissionResultOutboxListener} writes the result
 *       outbox row in the same transaction (P6-RESULT-001).</li>
 *   <li>{@code AFTER_COMMIT}: {@code ContestAdjudicationListener} applies contest scoring
 *       (D-04), and achievement listeners check for new badges.</li>
 * </ul>
 */
public class SubmissionJudgedEvent extends ApplicationEvent {

    private final String submissionId;
    private final String userId;
    private final Long problemId;
    private final String verdict;
    private final boolean accepted;
    private final Integer penaltySeconds;
    private final LocalDateTime judgedAt;
    /** P6-RESULT-001: fence generation for result outbox idempotency key */
    private final long generation;
    /** P6-RESULT-001: runtime in ms for result outbox */
    private final int runtimeMs;
    /** P6-RESULT-001: memory in MB for result outbox */
    private final double memoryMb;
    /** P6-RESULT-001: contest id if applicable, null otherwise */
    private final String contestId;

    public SubmissionJudgedEvent(Object source,
                                  String submissionId,
                                  String userId,
                                  Long problemId,
                                  String verdict,
                                  boolean accepted,
                                  Integer penaltySeconds,
                                  LocalDateTime judgedAt) {
        this(source, submissionId, userId, problemId, verdict, accepted, penaltySeconds,
             judgedAt, 0, 0, 0, null);
    }

    public SubmissionJudgedEvent(Object source,
                                  String submissionId,
                                  String userId,
                                  Long problemId,
                                  String verdict,
                                  boolean accepted,
                                  Integer penaltySeconds,
                                  LocalDateTime judgedAt,
                                  long generation,
                                  int runtimeMs,
                                  double memoryMb,
                                  String contestId) {
        super(source);
        this.submissionId = submissionId;
        this.userId = userId;
        this.problemId = problemId;
        this.verdict = verdict;
        this.accepted = accepted;
        this.penaltySeconds = penaltySeconds;
        this.judgedAt = judgedAt;
        this.generation = generation;
        this.runtimeMs = runtimeMs;
        this.memoryMb = memoryMb;
        this.contestId = contestId;
    }

    public String getSubmissionId() { return submissionId; }
    public String getUserId() { return userId; }
    public Long getProblemId() { return problemId; }
    public String getVerdict() { return verdict; }
    public boolean isAccepted() { return accepted; }
    public Integer getPenaltySeconds() { return penaltySeconds; }
    public LocalDateTime getJudgedAt() { return judgedAt; }
    public long getGeneration() { return generation; }
    public int getRuntimeMs() { return runtimeMs; }
    public double getMemoryMb() { return memoryMb; }
    public String getContestId() { return contestId; }
}
