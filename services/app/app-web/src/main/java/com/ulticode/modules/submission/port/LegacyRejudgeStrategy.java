package com.ulticode.modules.submission.port;

import com.ulticode.app.api.dto.RejudgeResult;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.JudgeEnqueuePort;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;

 /**
  * The legacy (pre-ADR-003) rejudge path. It retains the non-fenced enqueue
  * flow, but advances the persisted generation so durable result events keep a
  * distinct identity for every rejudge.
  */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyRejudgeStrategy {

    private static final int MAX_GENERATION_BUMP_RETRIES = 3;

    private final SubmissionMapper submissionMapper;
    private final JudgeEnqueuePort judgeEnqueuePort;

    /**
     * Run the legacy non-transactional rejudge on {@code submission}.
     *
     * @param submission the submission to rejudge
     * @param result    the result DTO to populate
     * @return the result DTO
     */
    public RejudgeResult rejudge(Submission submission, RejudgeResult result) {
        String id = submission.getId();
        try {
            Submission enqueueTarget = advanceGeneration(submission);
            enqueueTarget.setStatus("Pending");
            int retryCount = enqueueTarget.getRetryCount() != null
                    ? enqueueTarget.getRetryCount() + 1
                    : 1;
            submissionMapper.bumpRetryCount(id, 1);
            enqueueTarget.setRetryCount(retryCount);

            enqueueAfterCommit(enqueueTarget);
            result.setSuccess(true);
            result.setNewStatus("Pending");
            result.setRejudgedAt(Instant.now());
            result.setRetryCount(enqueueTarget.getRetryCount());
            log.info("Rejudge initiated for submission: {} (retryCount={})",
                    id, enqueueTarget.getRetryCount());
        } catch (Exception e) {
            log.error("Failed to enqueue rejudge for submission: {}", id, e);
            result.setSuccess(false);
            result.setError(e.getMessage());
            result.setErrorCode(AppErrorCode.UNEXPECTED_APP_STATE.code());
        }

        // AuditContext not available in backend-app; audit context stripped during relocation
        return result;
    }

    private void enqueueAfterCommit(Submission submission) {
        Runnable enqueue = () -> judgeEnqueuePort.enqueueJudgeJob(
                submission.getId(),
                String.valueOf(submission.getProblemId()),
                submission.getUserId(),
                submission.getLanguage(),
                submission.getCode());
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            enqueue.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            enqueue.run();
                        } catch (Exception e) {
                            log.warn("Post-commit legacy rejudge enqueue failed for submission {}: {}",
                                    submission.getId(), e.getMessage());
                        }
                    }
                });
    }


    private Submission advanceGeneration(Submission submission) {
        Submission candidate = submission;
        for (int attempt = 0; attempt < MAX_GENERATION_BUMP_RETRIES; attempt++) {
            long expectedGeneration = candidate.getGeneration() != null
                    ? candidate.getGeneration()
                    : 1L;
            long newGeneration = expectedGeneration + 1L;
            if (submissionMapper.bumpGenerationAndReset(
                    candidate.getId(), expectedGeneration, newGeneration) == 1) {
                candidate.setGeneration(newGeneration);
                return candidate;
            }

            candidate = submissionMapper.selectById(candidate.getId());
            if (candidate == null) {
                throw new IllegalStateException(
                        "Submission " + submission.getId() + " disappeared during rejudge");
            }
        }
        throw new IllegalStateException(
                "Concurrent generation changes prevented rejudge for submission "
                        + submission.getId());
    }
}
