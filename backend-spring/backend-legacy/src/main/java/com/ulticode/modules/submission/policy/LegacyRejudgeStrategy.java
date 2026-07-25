package com.ulticode.modules.submission.policy;

import com.ulticode.common.util.AuditContext;
import com.ulticode.modules.admin.dto.RejudgeResult;
import com.ulticode.modules.queue.service.QueueService;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * The legacy (pre-ADR-003) non-transactional rejudge path, lifted out of
 * {@code AdminSubmissionServiceImpl.rejudgeLegacy} as the sibling strategy to
 * {@code DefaultRejudgePolicy}'s fenced path.
 *
 * <p>On enqueue failure the DB row stays {@code Pending} (orphan), matching
 * the historical contract. Preserved verbatim so flag-off deployments
 * observe no behavior change &mdash; the only change is <em>where</em> the
 * body lives: behind the {@link RejudgePolicy#rejudge} port, where both
 * branches are now reachable from one test surface.
 *
 * <p>The fenced vs legacy "not the same shape" invariant from red-team
 * CR &sect;3.2 is preserved: the two paths remain separate classes with
 * separate transaction semantics. The policy selects; it does not merge.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyRejudgeStrategy {

    private final SubmissionMapper submissionMapper;
    private final QueueService queueService;

    /**
     * Run the legacy non-transactional rejudge on {@code submission}.
     *
     * @param submission the submission to rejudge (must be non-null)
     * @param result     the result DTO to populate
     * @return the same result DTO with success/error populated
     */
    public RejudgeResult rejudge(Submission submission, RejudgeResult result) {
        String id = submission.getId();
        try {
            // Reset submission status to Pending for re-evaluation
            submission.setStatus("Pending");

            // D-23: Increment retry count to track rejudge attempts
            submission.setRetryCount(
                submission.getRetryCount() != null ? submission.getRetryCount() + 1 : 1
            );
            submissionMapper.updateById(submission);

            // D-04: Enqueue after DB update to avoid orphaned jobs on DB failure
            queueService.enqueueJudgeJob(
                submission.getId(),
                String.valueOf(submission.getProblemId()),
                submission.getUserId(),
                submission.getLanguage(),
                submission.getCode()
            );

            result.setSuccess(true);
            result.setNewStatus("Pending");
            // Surface rejudge metadata to the caller so the admin UI can
            // detect that a rejudge actually happened even when old and
            // new status are identical (e.g. Pending -> Pending).
            result.setRejudgedAt(Instant.now());
            result.setRetryCount(submission.getRetryCount());
            log.info("Rejudge initiated for submission: {} (retryCount={})",
                id, submission.getRetryCount());
        // broad catch: all failures map to same error response
        } catch (Exception e) {
            log.error("Failed to enqueue rejudge for submission: {}", id, e);
            result.setSuccess(false);
            result.setError(e.getMessage());
        }

        if (result.getSuccess()) {
            AuditContext.setOldValues(Map.of(
                "oldStatus", result.getOldStatus() != null ? result.getOldStatus() : "",
                "retryCount", submission.getRetryCount() != null ? submission.getRetryCount() : 0
            ));
            AuditContext.setNewValues(Map.of(
                "newStatus", "Pending",
                "retryCount", submission.getRetryCount() != null ? submission.getRetryCount() : 0
            ));
        }

        return result;
    }
}
