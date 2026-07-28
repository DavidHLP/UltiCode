package com.ulticode.modules.admin.service.impl;

import com.ulticode.common.annotation.Audited;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.modules.admin.dto.BatchRejudgeResponse;
import com.ulticode.modules.admin.dto.RejudgeResult;
import com.ulticode.modules.admin.service.AdminSubmissionService;
import com.ulticode.modules.admin.port.AdminSubmissionReadPort;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.port.RejudgePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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

    private final AdminSubmissionReadPort submissionReadPort;
    /**
     * C2 / architecture-review #3: the rejudge policy owns strategy
     * selection (fenced vs legacy) behind one {@link RejudgePolicy#rejudge}
     * port. This service is now a 3-line dispatch: load submission, build
     * the result DTO, hand both to the policy. Both branches are testable
     * through the policy without this service in the loop.
     */
    private final RejudgePolicy rejudgePolicy;

    @Override
    @Audited(action = AuditVocabulary.REQUEUE_SUBMISSION, entityType = AuditVocabulary.ENTITY_SUBMISSION, userIdFrom = "id")
    public RejudgeResult rejudge(String id, boolean notifyUser) {
        Submission submission = submissionReadPort.findById(id);
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
        return rejudgePolicy.rejudge(submission, result);
    }

    /**
     * Batch rejudge — intentionally NOT routed through
     * {@link com.ulticode.modules.admin.bulk.AdminBulkExecutor}.
     *
     * <p>Architecture-review candidate #1 lists this method as a victim of
     * the duplicated bulk loop, but the executor's contract is a per-id
     * {@code Consumer<String>} producing a uniform success/error outcome.
     * This method's per-id result is a rich {@link RejudgeResult} (carrying
     * {@code oldStatus}, {@code newStatus}, {@code retryCount},
     * {@code rejudgedAt}) that the admin UI renders, and each iteration
     * delegates to {@link #rejudge(String, boolean)} which already isolates
     * failures internally. Forcing the rich result through the executor's
     * void-action aggregated shape would erase that enrichment and
     * double-wrap the error handling. The 15-LOC loop here is a thin
     * delegating counter, not the switch-shaped dispatch the executor
     * targets &mdash; so it is left as-is. Withdrawn with evidence per the
     * architecture-review closure.
     */
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
