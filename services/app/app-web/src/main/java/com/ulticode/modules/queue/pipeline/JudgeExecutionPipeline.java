package com.ulticode.modules.queue.pipeline;

/**
 * Internal seam that owns the judge execution path: load test cases,
 * dispatch to the sandbox, resolve the verdict, and extract metrics.
 *
 * Extracted from {@code JudgeWorkerProcessor} (arch review candidate #1)
 * so the verdict-resolution logic has its own test surface — tests can
 * exercise the pipeline with two stand-ins (sandbox + mapper) instead of
 * the worker's 16+ collaborators.
 *
 * The worker retains queue polling, lease fencing, result persistence,
 * and WebSocket push. The pipeline is purely "what verdict did this
 * code get?"
 */
public interface JudgeExecutionPipeline {

    /**
     * Execute the judging pipeline for a single submission.
     *
     * @param language    the submission language slug
     * @param code        the source code
     * @param problemId   the problem ID (numeric)
     * @param userId      the submitter's user ID
     * @param submissionId the submission ID (for logging only)
     * @return the execution result, or {@code null} if no eligible test cases
     *         were found (caller should write System Error)
     * @throws Exception if the sandbox or verdict resolution fails
     */
    JudgeExecutionResult execute(
            String language,
            String code,
            long problemId,
            String userId,
            String submissionId
    ) throws Exception;
}
