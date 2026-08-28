package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.RunResultDTO;
import com.ulticode.app.api.dto.RunSubmissionDTO;

/** Narrow cross-process seam for synchronous code preview execution. */
public interface CodeExecutionPort {

    /**
     * Execute a run submission in the Judge-owned sandbox.
     *
     * @param runDto run parameters
     * @param problemId problem ID for test case lookup
     * @param userId submitting user ID
     * @return execution result
     */
    RunResultDTO execute(RunSubmissionDTO runDto, Long problemId, String userId);
}
