package com.ulticode.submission.api.service;

import com.ulticode.domain.submission.enums.SubmissionStatus;

/** Judge-facing commands that persist submission status and verdicts. */
public interface SubmissionVerdictWritePort {

    /** Legacy unfenced status/verdict writer; automatic retries are forbidden. */
    void updateSubmissionResult(String submissionId, SubmissionStatus status,
                                int runtime, Double memory, String testDetailsJson);

    /** Generation/attempt-fenced verdict writer; false means stale/superseded. */
    boolean updateSubmissionResultFenced(String submissionId, SubmissionStatus status,
                                         int runtime, Double memory, String testDetailsJson,
                                         long generation, String attemptId);
}
