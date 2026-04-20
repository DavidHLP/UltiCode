package com.ulticode.modules.submission.service;

import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;

import java.util.List;

/**
 * Service interface for Docker sandbox execution.
 * Handles Docker process lifecycle, security parameters, and timeout management.
 */
public interface SandboxService {

    /**
     * Execute a single test case in a Docker sandbox.
     *
     * @param language  the programming language
     * @param code     the source code
     * @param testCase the test case with inputs/expected output
     * @param runId    the execution run ID
     * @param userId   the user ID
     * @return the result of the sandbox execution
     */
    RunResultDTO.RunCaseResult executeInSandbox(String language, String code,
                                                RunSubmissionDTO.RunTestCase testCase,
                                                String runId, String userId);

    /**
     * Execute multiple test cases in a single Docker container.
     *
     * @param language  the programming language
     * @param code      the source code
     * @param testCases list of test cases
     * @param runId     the execution run ID
     * @param userId    the user ID
     * @return list of results for each test case
     */
    List<RunResultDTO.RunCaseResult> executeBatch(String language, String code,
                                                  List<RunSubmissionDTO.RunTestCase> testCases,
                                                  String runId, String userId);

    /**
     * Build Docker CLI command for single-case execution.
     *
     * @param language the programming language
     * @param code     the source code
     * @return the Docker CLI argument list
     */
    List<String> buildDockerCommand(String language, String code);

    /**
     * Build Docker CLI command for batch execution.
     *
     * @param language       the programming language
     * @param wrapperScript the generated wrapper script content
     * @return the Docker CLI argument list
     */
    List<String> buildBatchDockerCommand(String language, String wrapperScript);
}
