package com.ulticode.app.api.service;

/**
 * Write port through which the submission module enqueues a judge job,
 * decoupling it from the queue module's {@code QueueService}.
 *
 * <p>The queue module supplies the only production implementation
 * ({@code QueueService}); tests supply an in-memory fake.
 */
public interface JudgeEnqueuePort {

    /**
     * Enqueue a judge job for the given submission.
     *
     * @param submissionId the submission id
     * @param problemId    the target problem id (as string for queue routing)
     * @param userId       the submitting user id
     * @param language     the submission language
     * @param code         the source code
     */
    void enqueueJudgeJob(String submissionId, String problemId, String userId,
                         String language, String code);
}
