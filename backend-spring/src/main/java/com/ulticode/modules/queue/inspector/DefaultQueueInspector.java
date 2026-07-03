package com.ulticode.modules.queue.inspector;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.queue.constants.QueueConstants;
import com.ulticode.modules.queue.dto.JobStatusDTO;
import com.ulticode.modules.queue.dto.QueueStatsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RQueue;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultQueueInspector implements QueueInspector {

    private final RQueue<Object> judgeQueue;
    private final RQueue<Object> emailQueue;
    private final RQueue<Object> notificationQueue;
    private final RedisTemplate<String, Object> jobStatusRedisTemplate;

    @Override
    public JobStatusDTO getJobStatus(String jobId) {
        String key = QueueConstants.JOB_STATUS_PREFIX + jobId;
        Object status = jobStatusRedisTemplate.opsForValue().get(key);

        if (status == null) {
            throw new BusinessException(ErrorCode.QUEUE_JOB_NOT_FOUND,
                    "Job not found: " + jobId);
        }

        if (status instanceof JobStatusDTO) {
            return (JobStatusDTO) status;
        }

        throw new BusinessException(ErrorCode.QUEUE_JOB_NOT_FOUND,
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
            default -> throw new BusinessException(ErrorCode.QUEUE_NOT_FOUND,
                    "Queue not found: " + queueName);
        };
    }
}
