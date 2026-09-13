package com.ulticode.modules.admin.service;

import com.ulticode.submission.api.command.BatchRejudgeCommand;
import com.ulticode.submission.api.command.RejudgeCommand;
import com.ulticode.submission.api.dto.BatchRejudgeResultDTO;
import com.ulticode.submission.api.dto.RejudgeResultDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.submission.api.service.SubmissionAdministrationService;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.dto.BatchRejudgeResponse;
import com.ulticode.modules.admin.dto.RejudgeResult;
import com.ulticode.modules.admin.write.AdminOwnerErrorMapper;
import com.ulticode.modules.admin.write.AdminWriteEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.ulticode.common.rpc.RpcPolicy;

/** Admin BFF adapter that always sends rejudge intent to backend-submission. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionCutoverService {

    private final CurrentUserProvider currentUserProvider;

    @DubboReference(group = "backend-submission", version = "1.0.0",
            timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private SubmissionAdministrationService dubboProvider;

    public RejudgeResult rejudge(String id, boolean notifyUser) {
        return rejudge(id, notifyUser, null);
    }

    public RejudgeResult rejudge(String id, boolean notifyUser, String requestedKey) {
        String actorId = currentUserProvider.getCurrentUserId();
        AdminWriteEnvelope envelope = AdminWriteEnvelope.envelope(
                "rejudge", requestedKey, actorId, currentUserProvider, "cutover rejudge");
        RpcResult<RejudgeResultDTO> result = callRejudge(new RejudgeCommand(
                envelope.commandId(), envelope.idempotency(), envelope.actor(),
                envelope.trace(), id, notifyUser));
        if (result == null || !result.success()) {
            throw AdminOwnerErrorMapper.mapOwnerError(AdminOwnerErrorMapper.Owner.SUBMISSION, result);
        }
        return mapResult(result.data());
    }

    public BatchRejudgeResponse batchRejudge(List<String> submissionIds, boolean notifyUsers) {
        return batchRejudge(submissionIds, notifyUsers, null);
    }

    public BatchRejudgeResponse batchRejudge(
            List<String> submissionIds, boolean notifyUsers, String requestedKey) {
        String actorId = currentUserProvider.getCurrentUserId();
        AdminWriteEnvelope envelope = AdminWriteEnvelope.envelope(
                "batchRejudge", requestedKey, actorId, currentUserProvider, "cutover batch rejudge");
        RpcResult<BatchRejudgeResultDTO> result = callBatchRejudge(new BatchRejudgeCommand(
                envelope.commandId(), envelope.idempotency(), envelope.actor(),
                envelope.trace(), submissionIds, notifyUsers));
        if (result == null || !result.success()) {
            throw AdminOwnerErrorMapper.mapOwnerError(AdminOwnerErrorMapper.Owner.SUBMISSION, result);
        }
        BatchRejudgeResultDTO dto = result.data();
        if (dto == null) {
            throw new BusinessException(
                    AdminErrorCode.UNKNOWN_ERROR, "RPC returned no batch result");
        }
        BatchRejudgeResponse response = new BatchRejudgeResponse();
        response.setTotal(dto.total());
        response.setSuccessful(dto.successful());
        response.setFailed(dto.failed());
        List<RejudgeResult> results = new ArrayList<>();
        if (dto.results() != null) {
            for (RejudgeResultDTO item : dto.results()) {
                results.add(mapResult(item));
            }
        }
        response.setResults(results);
        return response;
    }

    private RpcResult<RejudgeResultDTO> callRejudge(RejudgeCommand command) {
        try {
            return dubboProvider.rejudge(command);
        } catch (RuntimeException e) {
            log.error("Submission rejudge provider unavailable commandId={}",
                    command.commandId(), e);
            throw new BusinessException(
                    AdminErrorCode.UNKNOWN_ERROR, "Submission provider unavailable");
        }
    }

    private RpcResult<BatchRejudgeResultDTO> callBatchRejudge(BatchRejudgeCommand command) {
        try {
            return dubboProvider.batchRejudge(command);
        } catch (RuntimeException e) {
            log.error("Submission batch rejudge provider unavailable commandId={}",
                    command.commandId(), e);
            throw new BusinessException(
                    AdminErrorCode.UNKNOWN_ERROR, "Submission provider unavailable");
        }
    }

    private static RejudgeResult mapResult(RejudgeResultDTO dto) {
        RejudgeResult mapped = new RejudgeResult();
        if (dto == null) {
            mapped.setSuccess(false);
            mapped.setError("Missing rejudge result");
            mapped.setErrorCode(AppErrorCode.UNEXPECTED_APP_STATE.code());
            return mapped;
        }
        mapped.setSubmissionId(dto.submissionId());
        boolean success = !Boolean.FALSE.equals(dto.success());
        mapped.setSuccess(success);
        mapped.setNewStatus(dto.newStatus());
        mapped.setErrorCode(dto.errorCode());
        mapped.setError(dto.error());
        mapped.setRejudgedAt(success && dto.rejudgedAtEpochMs() > 0
                ? Instant.ofEpochMilli(dto.rejudgedAtEpochMs()) : null);
        mapped.setRetryCount(dto.retryCount());
        return mapped;
    }

}
