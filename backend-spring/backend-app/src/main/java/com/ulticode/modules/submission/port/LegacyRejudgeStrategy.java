package com.ulticode.modules.submission.port;

import com.ulticode.app.api.dto.RejudgeResult;
import com.ulticode.app.api.service.JudgeEnqueuePort;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Map;

/**
 * The legacy (pre-ADR-003) non-transactional rejudge path.
 *
 * <p>Preserved verbatim so flag-off deployments observe no behavior change.
 */
@Slf4j
@RequiredArgsConstructor
public class LegacyRejudgeStrategy {

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
            submission.setStatus("Pending");
            submission.setRetryCount(
                    submission.getRetryCount() != null ? submission.getRetryCount() + 1 : 1);
            submissionMapper.updateById(submission);

            judgeEnqueuePort.enqueueJudgeJob(
                    submission.getId(),
                    String.valueOf(submission.getProblemId()),
                    submission.getUserId(),
                    submission.getLanguage(),
                    submission.getCode());

            result.setSuccess(true);
            result.setNewStatus("Pending");
            result.setRejudgedAt(Instant.now());
            result.setRetryCount(submission.getRetryCount());
            log.info("Rejudge initiated for submission: {} (retryCount={})",
                    id, submission.getRetryCount());
        } catch (Exception e) {
            log.error("Failed to enqueue rejudge for submission: {}", id, e);
            result.setSuccess(false);
            result.setError(e.getMessage());
        }

        // AuditContext not available in backend-app; audit context stripped during relocation
        return result;
    }
}
