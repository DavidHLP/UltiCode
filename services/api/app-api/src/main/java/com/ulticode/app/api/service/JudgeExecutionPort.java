package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.RunSubmissionDTO;
import com.ulticode.domain.submission.enums.SubmissionStatus;

/**
 * Port through which the queue module drives the judge execution pipeline
 * owned by the submission module.
 *
 * <p>Per ADR-MIG-JUDGE, the execution pipeline (DefaultJudgeExecutionPipeline)
 * is co-located with the submission aggregate in backend-app. The queue module
 * receives judge jobs and delegates to this port rather than importing the
 * pipeline directly.
 */
public interface JudgeExecutionPort {

    /**
     * Execute the judging pipeline for a submission.
     *
     * @param submissionId the ID of the submission to judge; the impl resolves
     *                     the full Submission entity from storage
     * @param runSubmission the run submission payload (test cases, code, language)
     * @return the execution result
     */
    JudgeExecutionResult execute(String submissionId, RunSubmissionDTO runSubmission);

    /**
     * Result of a judge execution.
     */
    record JudgeExecutionResult(
            SubmissionStatus status,
            int runtimeMs,
            double memoryMb,
            String testDetails
    ) {}
}
