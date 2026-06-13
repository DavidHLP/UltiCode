package com.ulticode.modules.submission.service;

import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;

import java.util.List;

/**
 * Service interface for Docker sandbox execution.
 *
 * <p>Phase 5b: the Form A single/batch docker-command builders
 * ({@code buildDockerCommand}, {@code buildBatchDockerCommand}) were
 * removed — the D-form dispatch composes its docker command
 * internally, since the {@code sh -c} body is now a one-liner that
 * forwards to the pre-compiled harness inside the image.
 */
public interface SandboxService {

    /**
     * Execute a single test case in a Docker sandbox.
     *
     * @param language  the programming language
     * @param code      the source code
     * @param testCase  the test case with inputs/expected output
     * @param runId     the execution run ID
     * @param userId    the user ID
     * @return the per-case result of the sandbox execution
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
     * @return per-case results
     */
    List<RunResultDTO.RunCaseResult> executeBatch(String language, String code,
                                                  List<RunSubmissionDTO.RunTestCase> testCases,
                                                  String runId, String userId);
}
