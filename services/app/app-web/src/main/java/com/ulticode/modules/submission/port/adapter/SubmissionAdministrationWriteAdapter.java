package com.ulticode.modules.submission.port.adapter;

import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.RejudgePolicy;
import com.ulticode.modules.submission.dto.BatchRejudgeResponse;
import com.ulticode.modules.submission.dto.RejudgeResult;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.port.SubmissionAdministrationWritePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * App-side production adapter for the submission domain module's
 * {@link SubmissionAdministrationWritePort} (P7-RELOCATE).
 *
 * <p>Mirrors the legacy {@code LegacySubmissionWriteAdapter} semantics:
 * plain {@code selectById} read plus rejudge delegation. The rejudge flow
 * goes through the app-owned {@link RejudgePolicy} (fenced state machine)
 * exactly as {@code AdminSubmissionServiceImpl} does — the app-api
 * {@code RejudgeResult} is converted back to the domain DTO with the same
 * field mapping.
 */
@Component
@RequiredArgsConstructor
public class SubmissionAdministrationWriteAdapter implements SubmissionAdministrationWritePort {

    private final SubmissionMapper submissionMapper;
    private final RejudgePolicy rejudgePolicy;

    @Override
    public Submission selectById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return submissionMapper.selectById(id);
    }

    @Override
    public RejudgeResult rejudgeSubmission(String submissionId, boolean notifyUser) {
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            RejudgeResult result = new RejudgeResult();
            result.setSubmissionId(submissionId);
            result.setSuccess(false);
            result.setError("Submission not found");
            result.setErrorCode(AppErrorCode.CONTENT_NOT_FOUND.code());
            return result;
        }

        com.ulticode.app.api.dto.RejudgeResult portInput = new com.ulticode.app.api.dto.RejudgeResult();
        portInput.setSubmissionId(submissionId);
        portInput.setOldStatus(submission.getStatus());
        com.ulticode.app.api.dto.RejudgeResult portResult = rejudgePolicy.rejudge(submissionId, portInput);

        return toDomain(portResult);
    }

    @Override
    public BatchRejudgeResponse batchRejudgeSubmissions(List<String> submissionIds, boolean notifyUsers) {
        BatchRejudgeResponse response = new BatchRejudgeResponse();
        response.setTotal(submissionIds.size());
        response.setResults(new ArrayList<>(submissionIds.size()));
        int successful = 0;
        int failed = 0;

        for (String id : submissionIds) {
            RejudgeResult result = rejudgeSubmission(id, notifyUsers);
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
