package com.ulticode.modules.admin.service.impl;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.app.api.dto.SubmissionAdminRowDTO;
import com.ulticode.app.api.service.RejudgePolicy;
import com.ulticode.app.api.service.SubmissionAdminReadPort;
import com.ulticode.common.annotation.Audited;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.modules.admin.dto.BatchRejudgeResponse;
import com.ulticode.modules.admin.dto.RejudgeResult;
import com.ulticode.modules.admin.service.AdminSubmissionService;
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
 * mappers have left this file — the projection owns them.
 *
 * <p>P7-FIX-ADMIN-CONSUMERS-001: RejudgePolicy port signature changed from
 * {@code (Submission, admin.dto.RejudgeResult)} to
 * {@code (String, app.api.dto.RejudgeResult)} during SUBMISSION relocation.
 * This impl bridges the admin interface's legacy return type
 * ({@code admin.dto.RejudgeResult}) with the port's new API type.
 *
 * <p>ADMIN-004: the old-status read now goes through the entity-free
 * {@link SubmissionAdminReadPort} contract instead of the submission
 * entity/mapper.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSubmissionServiceImpl implements AdminSubmissionService {

    private final SubmissionAdminReadPort submissionReadPort;
    private final RejudgePolicy rejudgePolicy;

    @Override
    @Audited(action = AuditVocabulary.REQUEUE_SUBMISSION, entityType = AuditVocabulary.ENTITY_SUBMISSION, userIdFrom = "id")
    public RejudgeResult rejudge(String id, boolean notifyUser) {
        SubmissionAdminRowDTO submission = submissionReadPort.findById(id);
        if (submission == null) {
            RejudgeResult result = new RejudgeResult();
            result.setSubmissionId(id);
            result.setSuccess(false);
            result.setError("Submission not found");
            result.setErrorCode(AdminErrorCode.SUBMISSION_NOT_FOUND.code());
            return result;
        }

        com.ulticode.app.api.dto.RejudgeResult portInput = new com.ulticode.app.api.dto.RejudgeResult();
        portInput.setSubmissionId(id);
        portInput.setOldStatus(submission.status());
        com.ulticode.app.api.dto.RejudgeResult portResult = rejudgePolicy.rejudge(id, portInput);

        return toDomain(portResult);
    }

    @Override
    public BatchRejudgeResponse batchRejudge(List<String> submissionIds, boolean notifyUsers) {
        BatchRejudgeResponse response = new BatchRejudgeResponse();
        response.setTotal(submissionIds.size());
        response.setResults(new ArrayList<>(submissionIds.size()));
        int successful = 0;
        int failed = 0;

        for (String id : submissionIds) {
            RejudgeResult result = rejudge(id, notifyUsers);
            response.getResults().add(result);
            if (Boolean.TRUE.equals(result.getSuccess())) {
                successful++;
            } else {
                failed++;
            }
        }

        response.setSuccessful(successful);
        response.setFailed(failed);
        return response;
    }

    private RejudgeResult toDomain(com.ulticode.app.api.dto.RejudgeResult portResult) {
        if (portResult == null) {
            return null;
        }
        RejudgeResult domain = new RejudgeResult();
        domain.setSubmissionId(portResult.getSubmissionId());
        domain.setSuccess(portResult.getSuccess());
        domain.setOldStatus(portResult.getOldStatus());
        domain.setNewStatus(portResult.getNewStatus());
        domain.setError(portResult.getError());
        domain.setErrorCode(portResult.getErrorCode());
        domain.setRejudgedAt(portResult.getRejudgedAt());
        domain.setRetryCount(portResult.getRetryCount());
        return domain;
    }
}
