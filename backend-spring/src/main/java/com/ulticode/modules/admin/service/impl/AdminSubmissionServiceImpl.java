package com.ulticode.modules.admin.service.impl;

import com.ulticode.common.annotation.Audited;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.util.AuditContext;
import com.ulticode.modules.submission.config.FeatureFlagsProperties;
import com.ulticode.modules.admin.dto.BatchRejudgeResponse;
import com.ulticode.modules.admin.dto.RejudgeResult;
import com.ulticode.modules.admin.service.AdminSubmissionService;
import com.ulticode.modules.queue.service.QueueService;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.policy.RejudgePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Write-side implementation of {@link AdminSubmissionService}.
 *
 * <p>After the ADR-0011 Stage 2 extraction, this service owns only the
 * submission rejudge state machine (single + batch, ADR-003 fenced outbox +
 * generation bump). Every read-side concern (paginated list, single detail,
 * statistics, filter options) moved behind
 * {@link com.ulticode.modules.admin.projection.AdminSubmissionProjection}.
 * Cross-module entity imports ({@code User}, {@code Problem}) and their
 * mappers have left this file &mdash; the projection owns them.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSubmissionServiceImpl implements AdminSubmissionService {

    private final SubmissionMapper submissionMapper;
    private final QueueService queueService;
    private final FeatureFlagsProperties featureFlags;
    /**
     * C2: the fenced rejudge state machine lifted out of this service. Owns
     * the generation CAS + lease revoke + afterCommit enqueue + outbox
     * double-write + 3-way status branch. The legacy non-transactional path
     * stays inline because, per the red team CR §3.2, fenced and legacy are
     * not the same shape — folding them into a single method would force
     * the policy to dispatch internally and lose the depth gain.
     */
    private final RejudgePolicy rejudgePolicy;

    @Override
    @Audited(action = AuditVocabulary.REQUEUE_SUBMISSION, entityType = AuditVocabulary.ENTITY_SUBMISSION, userIdFrom = "id")
    public RejudgeResult rejudge(String id, boolean notifyUser) {
        Submission submission = submissionMapper.selectById(id);
        if (submission == null) {
            RejudgeResult result = new RejudgeResult();
            result.setSubmissionId(id);
            result.setSuccess(false);
            result.setError("Submission not found");
            return result;
        }

        RejudgeResult result = new RejudgeResult();
        result.setSubmissionId(id);
        result.setOldStatus(submission.getStatus());

        // ADR-003 M3b: fenced rejudge path. When the generation fence flag is
        // off, fall through to the legacy non-transactional path so behavior is
        // byte-for-byte identical to the pre-fence implementation.
        if (!featureFlags.isUseGenerationFence()) {
            return rejudgeLegacy(submission, result);
        }
        return rejudgePolicy.rejudgeFenced(submission, result);
    }

    /**
     * Legacy rejudge path (pre-ADR-003). Non-transactional; on enqueue failure
     * the DB row stays Pending (orphan), matching the historical contract.
     * Preserved verbatim so flag-off deployments observe no behavior change.
     */
    private RejudgeResult rejudgeLegacy(Submission submission, RejudgeResult result) {
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
            AuditContext.setOldValues(java.util.Map.of(
                "oldStatus", result.getOldStatus() != null ? result.getOldStatus() : "",
                "retryCount", submission.getRetryCount() != null ? submission.getRetryCount() : 0
            ));
            AuditContext.setNewValues(java.util.Map.of(
                "newStatus", "Pending",
                "retryCount", submission.getRetryCount() != null ? submission.getRetryCount() : 0
            ));
        }

        return result;
    }

    @Override
    public BatchRejudgeResponse batchRejudge(List<String> submissionIds, boolean notifyUsers) {
        // Non-null, non-empty, and size<=50 are enforced by Bean Validation
        // on the controller (see BatchRejudgeRequest @NotEmpty/@Size and
        // @Valid on the @RequestBody), so we can drop the silent null/empty
        // branch that previously masked client bugs.
        BatchRejudgeResponse response = new BatchRejudgeResponse();
        response.setTotal(submissionIds.size());
        response.setResults(new ArrayList<>(submissionIds.size()));
        int successful = 0;
        int failed = 0;

        for (String id : submissionIds) {
            RejudgeResult result = rejudge(id, notifyUsers);
            response.getResults().add(result);
            if (result.getSuccess()) {
                successful++;
            } else {
                failed++;
            }
        }

        response.setSuccessful(successful);
        response.setFailed(failed);
        return response;
    }

}
