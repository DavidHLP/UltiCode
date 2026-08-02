package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.RunResultDTO;
import com.ulticode.app.api.dto.RunSubmissionDTO;

/**
 * Port through which the queue module's judge pipeline executes code
 * in the sandbox, without importing the submission module directly.
 *
 * <p>P7-RELOCATE-SUBMISSION-001: extracted when CodeExecutionService
 * relocated to backend-app.
 */
public interface CodeExecutionPort {

    /**
     * Execute a run submission in the sandbox.
     *
     * @param runDto run parameters
     * @param problemId problem ID for test case lookup
     * @param userId submitting user ID
     * @return execution result
     */
    RunResultDTO execute(RunSubmissionDTO runDto, Long problemId, String userId);
}
