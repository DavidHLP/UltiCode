package com.ulticode.modules.queue.processor;

import com.ulticode.modules.queue.job.JudgeJob;
import com.ulticode.modules.queue.port.JudgeJobHandle;
import com.ulticode.modules.queue.port.JudgeQueue;

/**
 * Single seam that owns the judge attempt lifecycle:
 * {@code claim → heartbeat → execute → verdict → contest effect → push → release}.
 *
 * <p>Extracted from {@code JudgeWorkerProcessor} (architecture-review candidate
 * #4) so the worker becomes a thin polling adapter and any back end (RQueue,
 * leased Streams queue, in-memory test queue) can drive the same lifecycle
 * without re-implementing it.
 *
 * <p>The implementation preserves the cutover between the legacy and fenced
 * paths inside the executor — callers do not need to know which one is active.
 *
 * @author ulticode
 */
public interface JudgeAttemptExecutor {

    /**
     * Run the full lifecycle for one job. The polling adapter owns the
     * poll-loop cadence; this method owns ordering, heartbeat scheduling,
     * verdict resolution, contest side-effects, push, and lease release.
     *
     * @param job  the job pulled from the queue
     * @param port the queue the job came from (may be {@code null} for the
     *             legacy RQueue path); passed in so the executor can call
     *             {@link JudgeQueue#ack(JudgeJobHandle)} / {@code nack} on
     *             the same port
     * @param handle the queue handle when the job came from a leased queue
     *                (may be {@code null} for the legacy path)
     */
    void runAttempt(JudgeJob job, JudgeQueue port, JudgeJobHandle handle);

    /**
     * Mark a job as exhausted: write the System-Error verdict and push the
     * failed-result notification. Called from the worker's {@code onFailure}
     * path when retries are exhausted.
     */
    void markExhausted(JudgeJob job);
}
