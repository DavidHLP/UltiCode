package com.ulticode.submission.admin;

import com.ulticode.app.api.error.AppErrorCode;

/** Typed result of a Submission-owned rejudge transition. */
public sealed interface RejudgeOutcome
        permits RejudgeOutcome.Initiated, RejudgeOutcome.Rejected {

    /** Rejudge was accepted and a new judging attempt was initiated. */
    record Initiated(
            String submissionId,
            String newStatus,
            long rejudgedAtEpochMs,
            int retryCount) implements RejudgeOutcome {
    }

    /** Rejudge was rejected before a new judging attempt was initiated. */
    record Rejected(AppErrorCode code, String message) implements RejudgeOutcome {
    }
}
