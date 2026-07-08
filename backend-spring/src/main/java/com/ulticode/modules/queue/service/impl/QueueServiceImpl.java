package com.ulticode.modules.queue.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.uuid.UuidGenerator;
import org.springframework.data.redis.core.RedisTemplate;
import com.ulticode.modules.queue.config.QueueConfig;
import com.ulticode.modules.queue.constants.QueueConstants;
import com.ulticode.modules.queue.dto.JobRequestDTO;
import com.ulticode.modules.queue.dto.JobStatusDTO;
import com.ulticode.modules.queue.inspector.QueueInspector;
import com.ulticode.modules.queue.job.JudgeJob;
import com.ulticode.modules.queue.service.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RQueue;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of QueueService using Redisson queues. Owns the
 * write-path contract: enqueue, cancel, retry, clear, update-status,
 * and poll-with-side-effect (which transitions a job to
 * {@code PROCESSING}). Pure read paths
 * ({@code getJobStatus}, {@code getQueueStats}, {@code getQueueSize})
 * delegate to {@link QueueInspector}; this class injects that
 * module for its own internal status reads.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueServiceImpl implements QueueService {

    private final Clock clock;
    private final UuidGenerator uuidGenerator;

    private final RQueue<Object> judgeQueue;
    private final RQueue<Object> emailQueue;
    private final RQueue<Object> notificationQueue;
    private final RedisTemplate<String, Object> jobStatusRedisTemplate;
    private final QueueConfig queueConfig;
    private final QueueInspector queueInspector;

    @Override
    public String enqueueJudgeJob(String submissionId, String problemId, String userId,
                                   String language, String code) {
        JudgeJob job = JudgeJob.create(submissionId, problemId, userId, language, code, clock, uuidGenerator);
        return enqueueJudgeJob(job);
    }

    @Override
    public String enqueueJudgeJob(JudgeJob job) {
        if (job.getId() == null || job.getId().isBlank()) {
            job.setId(uuidGenerator.newId());
        }
        if (job.getCreatedAt() == null) {
            job.setCreatedAt(LocalDateTime.now(clock));
        }
        job.setStatus(QueueConstants.JobStatus.PENDING);

        try {
            boolean added = judgeQueue.add(job);
            if (!added) {
                throw new BusinessException(ErrorCode.QUEUE_OPERATION_FAILED,
                        "Failed to add job to judge queue");
            }

            // Store job status for tracking
            if (queueConfig.isEnableStatusTracking()) {
                saveJobStatus(job.getId(), buildJudgeJobStatus(job));
            }

            log.info("Enqueued judge job: {} for submission: {}", job.getId(), job.getSubmissionId());
            return job.getId();
        } catch (BusinessException e) {
            throw e;
        // broad catch: all queue failures map to same error response
        } catch (Exception e) {
            log.error("Failed to enqueue judge job for submission: {}", job.getSubmissionId(), e);
            throw new BusinessException(ErrorCode.QUEUE_OPERATION_FAILED,
                    "Failed to enqueue judge job: " + e.getMessage());
        }
    }

    @Override
    public String enqueueJob(String queueName, JobRequestDTO request) {
        String jobId = uuidGenerator.newId();

        // Build job data
        Map<String, Object> jobData = new HashMap<>();
        jobData.put("id", jobId);
        jobData.put("jobType", request.getJobType());
        jobData.put("priority", request.getPriority());
        jobData.put("maxRetries", request.getMaxRetries());
        jobData.put("timeoutSeconds", request.getTimeoutSeconds());
        jobData.put("payload", request.getPayload());
        jobData.put("createdBy", request.getCreatedBy());
        jobData.put("metadata", request.getMetadata());
        jobData.put("createdAt", LocalDateTime.now(clock));
        jobData.put("status", QueueConstants.JobStatus.PENDING);

        RQueue<Object> queue = getQueue(queueName);

        try {
            boolean added = queue.add(jobData);
            if (!added) {
                throw new BusinessException(ErrorCode.QUEUE_OPERATION_FAILED,
                        "Failed to add job to queue: " + queueName);
            }

            // Store job status for tracking
            if (queueConfig.isEnableStatusTracking()) {
                JobStatusDTO status = JobStatusDTO.builder()
                        .jobId(jobId)
                        .jobType(request.getJobType())
                        .queueName(queueName)
                        .status(QueueConstants.JobStatus.PENDING)
                        .priority(request.getPriority())
                        .maxRetries(request.getMaxRetries())
                        .createdAt(LocalDateTime.now(clock))
                        .createdBy(request.getCreatedBy())
                        .payload(request.getPayload())
                        .build();
                saveJobStatus(jobId, status);
            }

            log.info("Enqueued job: {} to queue: {}", jobId, queueName);
            return jobId;
        } catch (BusinessException e) {
            throw e;
        // broad catch: all queue failures map to same error response
        } catch (Exception e) {
            log.error("Failed to enqueue job to queue: {}", queueName, e);
            throw new BusinessException(ErrorCode.QUEUE_OPERATION_FAILED,
                    "Failed to enqueue job: " + e.getMessage());
        }
    }

    @Override
    public void cancelJob(String jobId) {
        JobStatusDTO status = queueInspector.getJobStatus(jobId);

        if (status.getStatus() == QueueConstants.JobStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.QUEUE_OPERATION_FAILED,
                    "Cannot cancel a job that is currently processing");
        }

        status.setStatus(QueueConstants.JobStatus.CANCELLED);
        status.setCompletedAt(LocalDateTime.now(clock));
        saveJobStatus(jobId, status);

        log.info("Cancelled job: {}", jobId);
    }

    @Override
    public String retryJob(String jobId) {
        JobStatusDTO status = queueInspector.getJobStatus(jobId);

        if (status.getStatus() != QueueConstants.JobStatus.FAILED) {
            throw new BusinessException(ErrorCode.QUEUE_OPERATION_FAILED,
                    "Only failed jobs can be retried");
        }

        // Create a new job with the same payload
        JobRequestDTO request = JobRequestDTO.builder()
                .jobType(status.getJobType())
                .priority(status.getPriority())
                .maxRetries(status.getMaxRetries())
                .payload(status.getPayload())
                .createdBy(status.getCreatedBy())
                .build();

        return enqueueJob(status.getQueueName(), request);
    }

    @Override
    public Object pollJob(String queueName) {
        RQueue<Object> queue = getQueue(queueName);
        Object job = queue.poll();

        if (job != null && queueConfig.isEnableStatusTracking()) {
            // Update status to PROCESSING
            String jobId = extractJobId(job);
            if (jobId != null) {
                updateJobStatus(jobId, QueueConstants.JobStatus.PROCESSING.name(), null);
            }
        }

        return job;
    }

    @Override
    public void clearQueue(String queueName) {
        RQueue<Object> queue = getQueue(queueName);
        queue.clear();
        log.info("Cleared queue: {}", queueName);
    }

    @Override
    public void updateJobStatus(String jobId, String status, String error) {
        JobStatusDTO jobStatus = queueInspector.getJobStatus(jobId);

        QueueConstants.JobStatus newStatus = QueueConstants.JobStatus.valueOf(status);
        jobStatus.setStatus(newStatus);
        jobStatus.setError(error);

        if (newStatus == QueueConstants.JobStatus.PROCESSING) {
            jobStatus.setStartedAt(LocalDateTime.now(clock));
            jobStatus.setAttempts(jobStatus.getAttempts() + 1);
        } else if (newStatus == QueueConstants.JobStatus.COMPLETED ||
                   newStatus == QueueConstants.JobStatus.FAILED ||
                   newStatus == QueueConstants.JobStatus.CANCELLED) {
            jobStatus.setCompletedAt(LocalDateTime.now(clock));
            if (jobStatus.getStartedAt() != null) {
                long durationMs = java.time.Duration.between(
                        jobStatus.getStartedAt(),
                        jobStatus.getCompletedAt()
                ).toMillis();
                jobStatus.setDurationMs(durationMs);
            }
        }

        saveJobStatus(jobId, jobStatus);
        log.debug("Updated job {} status to {}", jobId, status);
    }

    /**
     * Get the appropriate queue by name.
     *
     * @param queueName the queue name
     * @return the RQueue instance
     */
    private RQueue<Object> getQueue(String queueName) {
        return switch (queueName) {
            case QueueConstants.JUDGE_QUEUE -> judgeQueue;
            case QueueConstants.EMAIL_QUEUE -> emailQueue;
            case QueueConstants.NOTIFICATION_QUEUE -> notificationQueue;
            default -> throw new BusinessException(ErrorCode.QUEUE_NOT_FOUND,
                    "Queue not found: " + queueName);
        };
    }

    /**
     * Save job status to Redis.
     *
     * @param jobId  the job ID
     * @param status the job status
     */
    private void saveJobStatus(String jobId, JobStatusDTO status) {
        String key = QueueConstants.JOB_STATUS_PREFIX + jobId;
        jobStatusRedisTemplate.opsForValue().set(
                key, status, queueConfig.getJobStatusTtlSeconds(), TimeUnit.SECONDS);
    }

    /**
     * Build JobStatusDTO from JudgeJob.
     *
     * @param job the judge job
     * @return the job status DTO
     */
    private JobStatusDTO buildJudgeJobStatus(JudgeJob job) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("submissionId", job.getSubmissionId());
        payload.put("problemId", job.getProblemId());
        payload.put("language", job.getLanguage());

        return JobStatusDTO.builder()
                .jobId(job.getId())
                .jobType("JUDGE")
                .queueName(QueueConstants.JUDGE_QUEUE)
                .status(job.getStatus())
                .priority(job.getPriority())
                .maxRetries(job.getMaxRetries())
                .attempts(job.getAttempts())
                .createdAt(job.getCreatedAt())
                .userId(job.getUserId())
                .payload(payload)
                .build();
    }

    /**
     * Extract job ID from a job object.
     *
     * @param job the job object
     * @return the job ID, or null if not extractable
     */
    @SuppressWarnings("unchecked")
    private String extractJobId(Object job) {
        if (job instanceof JudgeJob judgeJob) {
            return judgeJob.getId();
        }
        if (job instanceof Map<?, ?> map) {
            Object id = map.get("id");
            return id != null ? id.toString() : null;
        }
        return null;
    }
}
