package com.ulticode.modules.queue.service;

import com.ulticode.modules.queue.dto.JobRequestDTO;
import com.ulticode.modules.queue.job.JudgeJob;

/**
 * Service interface for queue operations.
 * Provides methods for enqueuing jobs, managing job lifecycle, and
 * poll-with-side-effect. Read-only inspection (job status look-up,
 * queue size, queue statistics) lives on
 * {@link com.ulticode.modules.queue.inspector.QueueInspector} —
 * keeping the write-path contract here uncluttered.
 */
public interface QueueService {

    /**
     * Enqueue a judge job for code evaluation.
     *
     * @param submissionId the submission ID
     * @param problemId    the problem ID
     * @param userId       the user ID
     * @param language     the programming language
     * @param code         the source code
     * @return the job ID
     */
    String enqueueJudgeJob(String submissionId, String problemId, String userId,
                           String language, String code);

    /**
     * Enqueue a judge job with a pre-built JudgeJob object.
     *
     * @param job the judge job to enqueue
     * @return the job ID
     */
    String enqueueJudgeJob(JudgeJob job);

    /**
     * Enqueue a generic job.
     *
     * @param queueName the queue name
     * @param request   the job request
     * @return the job ID
     */
    String enqueueJob(String queueName, JobRequestDTO request);

    /**
     * Cancel a job.
     *
     * @param jobId the job ID to cancel
     */
    void cancelJob(String jobId);

    /**
     * Retry a failed job.
     *
     * @param jobId the job ID to retry
     * @return the new job ID
     */
    String retryJob(String jobId);

    /**
     * Get the next job from a queue (for processing).
     *
     * @param queueName the queue name
     * @return the next job, or null if queue is empty
     */
    Object pollJob(String queueName);

    /**
     * Clear all jobs from a queue.
     *
     * @param queueName the queue name
     */
    void clearQueue(String queueName);

    /**
     * Update job status.
     *
     * @param jobId  the job ID
     * @param status the new status
     * @param error  the error message (if failed)
     */
    void updateJobStatus(String jobId, String status, String error);
}
