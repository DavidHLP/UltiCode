package com.ulticode.modules.queue.inspector;
import com.ulticode.app.error.QueueErrorCode;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.queue.constants.QueueConstants;
import com.ulticode.modules.queue.dto.JobStatusDTO;
import com.ulticode.app.api.dto.ProbeStatus;
import com.ulticode.app.api.dto.QueueHealthSnapshotDTO;
import com.ulticode.modules.queue.dto.QueueStatsDTO;
import com.ulticode.submission.api.queue.JudgeQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RQueue;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Default adapter for {@link QueueInspector}. Side-effect free:
 * reads from Redis and Redisson queues only.
 *
 * <p>Keeps its own copy of the queue-name → {@code RQueue} mapping
 * so that {@link com.ulticode.modules.queue.service.QueueService}
 * does not need to expose a queue resolver to satisfy inspector
 * callers. The switch is three cases; the duplication is intentional
 * to keep the read module independent of the write module's bean
 * graph.
 *
 * <p>Backend normalization: when
 * {@code app.features.judge-queue.use-port=true} the {@link JudgeQueue}
 * bean is present and the real judge dispatch path writes to the
 * {@code judge:stream} Redis Stream rather than the legacy
 * {@code judge_queue} {@code RQueue}. In that mode the
 * {@code RQueue.size()} reads zero (no writer), so
 * {@link #getQueueHealthSnapshot(String)} sources the judge-queue
 * depth from {@link JudgeQueue#pendingDepth()} (XPENDING total). In
 * the legacy mode the {@code RQueue.size()} path is authoritative.
 * Either way the snapshot has one shape; the caller does not need to
 * know which backend is live.
 */
@Slf4j
@Service
@org.springframework.context.annotation.Profile("!test")
@RequiredArgsConstructor
public class DefaultQueueInspector implements QueueInspector {

    private final RQueue<Object> judgeQueue;
    private final RQueue<Object> emailQueue;
    private final RQueue<Object> notificationQueue;
    private final RedisTemplate<String, Object> jobStatusRedisTemplate;

    /**
     * Present only when
     * {@code app.features.judge-queue.use-port=true}. Resolves to null
     * via {@link ObjectProvider#getIfAvailable()} in the legacy mode; the
     * inspector then reports {@code RQueue.size()} for the judge queue.
     */
    private final ObjectProvider<JudgeQueue> judgeQueueProvider;

    @Override
    public JobStatusDTO getJobStatus(String jobId) {
        String key = QueueConstants.JOB_STATUS_PREFIX + jobId;
        Object status = jobStatusRedisTemplate.opsForValue().get(key);

        if (status == null) {
            throw new BusinessException(QueueErrorCode.QUEUE_JOB_NOT_FOUND,
                    "Job not found: " + jobId);
        }

        if (status instanceof JobStatusDTO) {
            return (JobStatusDTO) status;
        }

        throw new BusinessException(QueueErrorCode.QUEUE_JOB_NOT_FOUND,
                "Invalid job status data for: " + jobId);
    }

    @Override
    public QueueStatsDTO getQueueStats(String queueName) {
        RQueue<Object> queue = resolveQueue(queueName);

        return QueueStatsDTO.builder()
                .queueName(queueName)
                .waitingCount(queue.size())
                .paused(false)
                .build();
    }

    @Override
    public long getQueueSize(String queueName) {
        return resolveQueue(queueName).size();
    }

    @Override
    public QueueHealthSnapshotDTO getQueueHealthSnapshot(String queueName) {
        // Validate the name first: an unknown queue is a programming error,
        // not a probe failure, and must surface as QUEUE_NOT_FOUND.
        validateKnownQueue(queueName);

        // For the judge queue under use-port=true, RQueue.size() reads zero
        // (no writer) — source the depth from the Stream backend instead.
        if (QueueConstants.JUDGE_QUEUE.equals(queueName) && judgeQueueProvider.getIfAvailable() != null) {
            return probeStreamBackedJudgeQueue();
        }

        return probeRQueueBacked(queueName);
    }

    /**
     * Read the legacy {@code RQueue.size()} for one queue, translating any
     * Redis/Redisson failure into {@link ProbeStatus#PROBE_FAILED}.
     */
    private QueueHealthSnapshotDTO probeRQueueBacked(String queueName) {
        try {
            long depth = resolveQueue(queueName).size();
            return baseSnapshot(queueName, depth, ProbeStatus.OK);
        } catch (BusinessException e) {
            // resolveQueue threw QUEUE_NOT_FOUND — propagate, this is a
            // programming error not a probe failure.
            throw e;
        } catch (Exception e) {
            // broad catch: any Redis/Redisson failure must surface as
            // PROBE_FAILED so monitoring fails closed instead of folding
            // the failure into a misleading zero depth.
            log.warn("Queue probe failed for {} (RQueue backend): {}",
                    queueName, e.getMessage());
            return baseSnapshot(queueName, 0L, ProbeStatus.PROBE_FAILED);
        }
    }

    /**
     * Read the Stream-backed judge queue depth via the {@link JudgeQueue}
     * port, translating any failure into {@link ProbeStatus#PROBE_FAILED}.
     */
    private QueueHealthSnapshotDTO probeStreamBackedJudgeQueue() {
        JudgeQueue judgeQueue = judgeQueueProvider.getIfAvailable();
        if (judgeQueue == null) {
            // The provider resolved null between the caller's presence check
            // and here (should not happen for a singleton bean, but fail closed
            // like any other probe failure rather than dereferencing null).
            log.warn("JudgeQueue provider resolved null during judge_queue stream probe");
            return baseSnapshot(QueueConstants.JUDGE_QUEUE, 0L, ProbeStatus.PROBE_FAILED);
        }
        try {
            long depth = judgeQueue.pendingDepth();
            return baseSnapshot(QueueConstants.JUDGE_QUEUE, depth, ProbeStatus.OK);
        } catch (Exception e) {
            // broad catch: Stream probe failure (Redis down, group missing,
            // deserialization, etc.) must surface as PROBE_FAILED.
            log.warn("Queue probe failed for judge_queue (Stream backend): {}",
                    e.getMessage());
            return baseSnapshot(QueueConstants.JUDGE_QUEUE, 0L, ProbeStatus.PROBE_FAILED);
        }
    }

    private QueueHealthSnapshotDTO baseSnapshot(String queueName, long depth, ProbeStatus status) {
        // failedCount / completedCount are intentionally zero for now: deriving
        // them needs a bounded SCAN over `queue:job:*` filtered by job status,
        // which is deferred to keep the probe cheap and honor the Redis-storm
        // rules. Correctness of waitingDepth + provenance is the priority.
        return new QueueHealthSnapshotDTO(queueName, depth, 0L, 0L, status);
    }

    /**
     * Reject an unknown queue name before any probe runs. An unknown
     * name is a programming error in the caller (monitoring iterates a
     * hard-coded known list), not a probe failure.
     */
    private void validateKnownQueue(String queueName) {
        if (!QueueConstants.JUDGE_QUEUE.equals(queueName)
                && !QueueConstants.EMAIL_QUEUE.equals(queueName)
                && !QueueConstants.NOTIFICATION_QUEUE.equals(queueName)) {
            throw new BusinessException(QueueErrorCode.QUEUE_NOT_FOUND,
                    "Queue not found: " + queueName);
        }
    }

    /**
     * Map a queue name to its Redisson backing queue. Mirrors the
     * switch in {@code QueueServiceImpl}; the duplication is small
     * and keeps this read module from depending on the write module.
     */
    private RQueue<Object> resolveQueue(String queueName) {
        return switch (queueName) {
            case QueueConstants.JUDGE_QUEUE -> judgeQueue;
            case QueueConstants.EMAIL_QUEUE -> emailQueue;
            case QueueConstants.NOTIFICATION_QUEUE -> notificationQueue;
            default -> throw new BusinessException(QueueErrorCode.QUEUE_NOT_FOUND,
                    "Queue not found: " + queueName);
        };
    }
}
