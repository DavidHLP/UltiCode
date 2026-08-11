package com.ulticode.modules.submission.port.impl;

import com.ulticode.app.api.dto.RejudgeResult;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.ContestSubmissionPort;
import com.ulticode.app.api.service.JudgeEnqueuePort;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.config.FeatureFlagsProperties;
import com.ulticode.modules.submission.fence.SubmissionStateMachine;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.outbox.entity.JudgeOutboxRecord;
import com.ulticode.modules.submission.outbox.mapper.JudgeOutboxMapper;
import com.ulticode.modules.submission.port.LegacyRejudgeStrategy;
import com.ulticode.app.api.service.RejudgePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

/**
 * Default {@link RejudgePolicy} implementation.
 *
 * <p>Owns the ADR-003 M3b fenced rejudge state machine.
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor

public class DefaultRejudgePolicy implements RejudgePolicy {

    private final SubmissionMapper submissionMapper;
    private final ContestSubmissionPort contestSubmissionPort;
    private final JudgeEnqueuePort judgeEnqueuePort;
    private final JudgeOutboxMapper judgeOutboxMapper;
    private final FeatureFlagsProperties featureFlags;
    private final TransactionTemplate transactionTemplate;
    private final UuidGenerator uuidGenerator;
    private final LegacyRejudgeStrategy legacyRejudgeStrategy;

    @Override
    public RejudgeResult rejudge(String submissionId, RejudgeResult result) {
        if (contestSubmissionPort.isContestSubmission(submissionId)) {
            result.setSuccess(false);
            result.setError("Contest submissions cannot be rejudged until replacement scoring is supported");
            result.setErrorCode(AppErrorCode.CONTENT_STATE_CONFLICT.code());
            return result;
        }
        com.ulticode.modules.submission.entity.Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            result.setSuccess(false);
            result.setError("Submission not found");
            result.setErrorCode(AppErrorCode.CONTENT_NOT_FOUND.code());
            return result;
        }
        if (!featureFlags.isUseGenerationFence()) {
            return legacyRejudgeStrategy.rejudge(submission, result);
        }
        return rejudgeFenced(submission, result);
    }

    /**
     * Run the fenced rejudge flow on {@code submission}.
     *
     * @param submission the submission to rejudge
     * @param result     the result DTO to populate
     * @return the same result DTO
     */
    public RejudgeResult rejudgeFenced(Submission submission, RejudgeResult result) {
        String id = submission.getId();
        final boolean[] dispatchWon = {false};

        try {
            transactionTemplate.executeWithoutResult(status -> {
                SubmissionStatus current = SubmissionStatus.fromDbName(submission.getStatus());
                boolean judging = current == SubmissionStatus.JUDGING;
                boolean rejudgeable = SubmissionStateMachine.canAdminRejudgeFrom(current);

                submission.setRetryCount(
                        submission.getRetryCount() != null ? submission.getRetryCount() + 1 : 1);

                if (judging) {
                    if (submission.getCurrentAttemptId() != null) {
                        submissionMapper.forceLeaseExpiry(id, submission.getCurrentAttemptId());
                    }
                    submissionMapper.bumpRetryCount(id, 1);
                } else if (rejudgeable) {
                    long expectedGen = submission.getGeneration() != null ? submission.getGeneration() : 1L;
                    long newGen = expectedGen + 1;
                    int bumped = submissionMapper.bumpGenerationAndReset(id, expectedGen, newGen);
                    boolean bumpWon = bumped == 1;
                    submissionMapper.bumpRetryCount(id, 1);
                    if (bumpWon) {
                        submission.setGeneration(newGen);
                        writeRejudgeOutbox(submission, newGen);
                        dispatchWon[0] = true;
                    } else {
                        throw new org.springframework.transaction.TransactionSystemException(
                                "concurrent generation change for submission " + id);
                    }
                } else {
                    submissionMapper.bumpRetryCount(id, 1);
                    submission.setStatus("Pending");
                    writeRejudgeOutbox(submission,
                            submission.getGeneration() != null ? submission.getGeneration() : 1L);
                    dispatchWon[0] = true;
                }

                if (dispatchWon[0] && TransactionSynchronizationManager.isSynchronizationActive()) {
                    final Submission enqueueTarget = submission;
                    TransactionSynchronizationManager.registerSynchronization(
                            new TransactionSynchronization() {
                                @Override
                                public void afterCommit() {
                                    try {
                                        judgeEnqueuePort.enqueueJudgeJob(
                                                enqueueTarget.getId(),
                                                String.valueOf(enqueueTarget.getProblemId()),
                                                enqueueTarget.getUserId(),
                                                enqueueTarget.getLanguage(),
                                                enqueueTarget.getCode());
                                    } catch (Exception e) {
                                        log.warn("Post-commit enqueue failed for submission {} "
                                                        + "(outbox row recorded for replay): {}",
                                                enqueueTarget.getId(), e.getMessage());
                                    }
                                }
                            });
                } else if (dispatchWon[0]) {
                    judgeEnqueuePort.enqueueJudgeJob(
                            submission.getId(),
                            String.valueOf(submission.getProblemId()),
                            submission.getUserId(),
                            submission.getLanguage(),
                            submission.getCode());
                }
            });

            result.setSuccess(true);
            result.setNewStatus(judgingAfterRejudge(submission) ? "Judging" : "Pending");
            result.setRejudgedAt(Instant.now());
            result.setRetryCount(submission.getRetryCount());
            log.info("Fenced rejudge initiated for submission {} (retryCount={}, gen={})",
                    id, submission.getRetryCount(),
                    submission.getGeneration() != null ? submission.getGeneration() : 1L);
        } catch (Exception e) {
            log.error("Fenced rejudge failed for submission {}: {}", id, e.getMessage(), e);
            result.setSuccess(false);
            result.setError(e.getMessage());
            result.setErrorCode(AppErrorCode.UNEXPECTED_APP_STATE.code());
        }
        return result;
    }

    private void writeRejudgeOutbox(Submission submission, long generation) {
        if (featureFlags.isUseJudgeOutbox() && judgeOutboxMapper != null) {
            boolean isShadow = !featureFlags.getJudgeQueue().isUsePort();
            judgeOutboxMapper.insert(JudgeOutboxRecord.forResubmission(
                    submission,
                    String.valueOf(submission.getProblemId()),
                    generation,
                    isShadow,
                    uuidGenerator));
        }
    }

    private boolean judgingAfterRejudge(Submission submission) {
        SubmissionStatus s = SubmissionStatus.fromDbName(submission.getStatus());
        return s == SubmissionStatus.JUDGING;
    }
}
