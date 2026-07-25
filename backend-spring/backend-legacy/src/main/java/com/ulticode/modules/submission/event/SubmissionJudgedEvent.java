package com.ulticode.modules.submission.event;

import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * Event published when a submission's verdict has been finalized by the judge worker.
 * Consumed by {@link com.ulticode.modules.contest.listener.ContestScoringListener} after the
 * source transaction commits, so contest scoring/aggregation can react to AC/RE without
 * coupling SubmissionServiceImpl to the contest module.
 *
 * <p>Per the contest scoring design (see docs/contest-design-analysis-2026-06-16.md, P0-1):
 * the listener applies the verdict to {@code contest_submissions.is_accepted} and
 * aggregates {@code total_score / total_penalty / attempt_count} on
 * {@code contest_participants}, plus writes a {@code contest_problem_results} row and
 * (if first) a {@code first_solve_records} row.
 */
public class SubmissionJudgedEvent extends ApplicationEvent {

    private final String submissionId;
    private final String userId;
    private final Long problemId;
    private final String verdict;
    private final boolean accepted;
    private final Integer penaltySeconds;
    private final LocalDateTime judgedAt;

    public SubmissionJudgedEvent(Object source,
                                  String submissionId,
                                  String userId,
                                  Long problemId,
                                  String verdict,
                                  boolean accepted,
                                  Integer penaltySeconds,
                                  LocalDateTime judgedAt) {
        super(source);
        this.submissionId = submissionId;
        this.userId = userId;
        this.problemId = problemId;
        this.verdict = verdict;
        this.accepted = accepted;
        this.penaltySeconds = penaltySeconds;
        this.judgedAt = judgedAt;
    }

    public String getSubmissionId() { return submissionId; }
    public String getUserId() { return userId; }
    public Long getProblemId() { return problemId; }
    public String getVerdict() { return verdict; }
    public boolean isAccepted() { return accepted; }
    public Integer getPenaltySeconds() { return penaltySeconds; }
    public LocalDateTime getJudgedAt() { return judgedAt; }
}
