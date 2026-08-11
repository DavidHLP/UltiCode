package com.ulticode.app.api.event;

import org.springframework.context.ApplicationEvent;
import java.io.Serializable;

import java.time.LocalDateTime;

/**
 * Event published when a submission's verdict has been finalized by the judge worker.
 *
 * <p>The source transaction writes the durable result outbox in
 * {@code BEFORE_COMMIT}. Contest, notification, achievement, and WebSocket
 * consumers process the published durable event after commit.</p>
 */
public class SubmissionJudgedEvent extends ApplicationEvent implements Serializable {

    private final String submissionId;
    private final String userId;
    private final Long problemId;
    private final String verdict;
    private final boolean accepted;
    private final Integer penaltySeconds;
    private final LocalDateTime judgedAt;
    private final long generation;
    private final int runtimeMs;
    private final double memoryMb;
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
