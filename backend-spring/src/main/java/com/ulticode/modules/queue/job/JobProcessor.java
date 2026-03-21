package com.ulticode.modules.queue.job;

import com.ulticode.modules.queue.dto.JobStatusDTO;

/**
 * Interface for job processors.
 * Implement this interface to create custom job processors for different job types.
 *
 * @param <T> the type of job this processor handles
 */
public interface JobProcessor<T> {

    /**
     * Get the job type this processor handles.
     *
     * @return the job type identifier
     */
    String getJobType();

    /**
     * Process a job.
     *
     * @param job the job to process
     * @return the job status after processing
     * @throws Exception if processing fails
     */
    JobStatusDTO process(T job) throws Exception;

    /**
     * Called when a job fails.
     *
     * @param job   the job that failed
     * @param error the error that caused the failure
     */
    default void onFailure(T job, Exception error) {
        // Default implementation does nothing
    }

    /**
     * Called when a job completes successfully.
     *
     * @param job    the completed job
     * @param result the processing result
     */
    default void onComplete(T job, JobStatusDTO result) {
        // Default implementation does nothing
    }

    /**
     * Determine if a job should be retried.
     *
     * @param job      the job that failed
     * @param error    the error that caused the failure
     * @param attempts the number of attempts made so far
     * @param maxRetries the maximum retry attempts allowed
     * @return true if the job should be retried
     */
    default boolean shouldRetry(T job, Exception error, int attempts, int maxRetries) {
        return attempts < maxRetries;
    }
}
