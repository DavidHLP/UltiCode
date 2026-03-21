package com.ulticode.modules.queue.service;

import com.ulticode.modules.queue.dto.JobRequestDTO;
import com.ulticode.modules.queue.dto.JobStatusDTO;
import com.ulticode.modules.queue.dto.QueueStatsDTO;
import com.ulticode.modules.queue.job.JudgeJob;

/**
 * Service interface for queue operations.
 * Provides methods for enqueuing jobs, checking status, and managing queues.
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
     * Get the status of a job.
     *
     * @param jobId the job ID
     * @return the job status, or null if not found
     */
    JobStatusDTO getJobStatus(String jobId);

    /**
     * Get statistics for a queue.
     *
     * @param queueName the queue name
     * @return the queue statistics
     */
    QueueStatsDTO getQueueStats(String queueName);

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

    /**
     * Get the number of jobs waiting in a queue.
     *
     * @param queueName the queue name
     * @return the number of waiting jobs
     */
    long getQueueSize(String queueName);
}
