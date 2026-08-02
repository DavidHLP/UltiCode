package com.ulticode.modules.queue.inspector;

import com.ulticode.modules.queue.dto.JobStatusDTO;
import com.ulticode.app.api.dto.QueueHealthSnapshotDTO;
import com.ulticode.modules.queue.dto.QueueStatsDTO;

/**
 * Read-only inspection deep module for queue state.
 *
 * <p>Owns every pure-read path that asks the queue subsystem about
 * the world: job status look-up, queue size, queue statistics. The
 * interface is intentionally small so {@link com.ulticode.modules.queue.service.QueueService}
 * can keep its write-path contract (enqueue, cancel, retry, clear,
 * update, poll-with-side-effect) without dragging read concerns along.
 *
 * <p>Deliberately side-effect free: every method here returns a
 * snapshot and does not mutate Redis state. Write-with-side-effect
 * paths (e.g. {@code pollJob} which transitions a job to
 * {@code PROCESSING}) stay on {@code QueueService}.
 *
 * <p>Test surface: a unit test for this module mocks a
 * {@link org.springframework.data.redis.core.RedisTemplate} and the
 * three {@link org.redisson.api.RQueue} beans, with no need to
 * stub any write-path collaborators.
 */
public interface QueueInspector extends com.ulticode.app.api.service.QueueHealthProbePort {

    /**
     * Get the status of a job.
     *
     * @param jobId the job ID
     * @return the job status
     * @throws com.ulticode.common.exception.BusinessException with
     *         {@code QUEUE_JOB_NOT_FOUND} when the job is unknown
     *         or the stored payload is not a {@code JobStatusDTO}
     */
    JobStatusDTO getJobStatus(String jobId);

    /**
     * Get statistics for a queue.
     *
     * @param queueName the queue name (must match a known constant in
     *                  {@code QueueConstants})
     * @return the queue statistics
     * @throws com.ulticode.common.exception.BusinessException with
     *         {@code QUEUE_NOT_FOUND} when the queue name is unknown
     */
    QueueStatsDTO getQueueStats(String queueName);

    /**
     * Get the number of jobs waiting in a queue.
     *
     * @param queueName the queue name
     * @return the number of waiting jobs
     * @throws com.ulticode.common.exception.BusinessException with
     *         {@code QUEUE_NOT_FOUND} when the queue name is unknown
     */
    long getQueueSize(String queueName);

    /**
     * Read a monitoring-oriented health snapshot of one queue.
     *
     * <p>Unlike {@link #getQueueStats(String)} (which throws on an
     * unknown queue) this method is built for the monitoring health
     * check: it translates any Redis/Redisson probe failure into a
     * snapshot carrying {@link com.ulticode.app.api.dto.ProbeStatus#PROBE_FAILED}
     * so the caller can fail closed (surface unhealthy) rather than
     * fold the failure into a misleading zero depth.
     *
     * <p>For the judge queue under
     * {@code app.features.judge-queue.use-port=true}, the waiting depth
     * is sourced from the Stream backend (XPENDING total) so monitoring
     * sees one VO shape regardless of backend.
     *
     * @param queueName the queue name (must match a known constant in
     *                  {@code QueueConstants})
     * @return a non-null snapshot. The {@code probeStatus} field is the
     *         only authoritative signal when the broker is unreachable;
     *         the depth fields are informational only in that case
     * @throws com.ulticode.common.exception.BusinessException with
     *         {@code QUEUE_NOT_FOUND} when the queue name is unknown
     *         (this is a programming error, not a probe failure)
     */
    QueueHealthSnapshotDTO getQueueHealthSnapshot(String queueName);
}
