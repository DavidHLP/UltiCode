package com.ulticode.modules.admin.service;

import com.ulticode.common.command.ActorDelegation;
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
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.common.util.TraceIdUtil;
import com.ulticode.modules.admin.dto.BatchRejudgeResponse;
import com.ulticode.modules.admin.dto.RejudgeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * P4-CUTOVER-002: feature-flagged routing adapter for submission rejudge.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionCutoverService {

    private final AdminSubmissionService submissionService;
    private final CurrentUserProvider currentUserProvider;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = 3000, retries = 0, check = false)
    private SubmissionAdministrationService dubboProvider;

    @Value("${app.features.submission-dubbo-cutover:false}")
    private boolean dubboEnabled;

    public RejudgeResult rejudge(String id, boolean notifyUser) {
        return rejudge(id, notifyUser, null);
    }

    public RejudgeResult rejudge(String id, boolean notifyUser, String requestedKey) {
        if (!dubboEnabled) {
            return submissionService.rejudge(id, notifyUser);
        }
        String actorId = currentActorId();
        IdMetadata idempotency = idempotency(requestedKey);
        RpcResult<RejudgeResultDTO> result = callRejudge(new RejudgeCommand(
                commandId("rejudge", idempotency),
                idempotency,
                new ActorDelegation("ADMIN", actorId, actorId, "cutover rejudge"),
                currentTrace(), id, notifyUser));
        if (result == null || !result.success()) {
            throw mapError(result);
        }
        return mapResult(result.data());
    }

    public BatchRejudgeResponse batchRejudge(List<String> submissionIds, boolean notifyUsers) {
        return batchRejudge(submissionIds, notifyUsers, null);
    }

    public BatchRejudgeResponse batchRejudge(
            List<String> submissionIds, boolean notifyUsers, String requestedKey) {
        if (!dubboEnabled) {
            return submissionService.batchRejudge(submissionIds, notifyUsers);
        }
        String actorId = currentActorId();
        IdMetadata idempotency = idempotency(requestedKey);
        RpcResult<BatchRejudgeResultDTO> result = callBatchRejudge(new BatchRejudgeCommand(
                commandId("batchRejudge", idempotency),
                idempotency,
                new ActorDelegation("ADMIN", actorId, actorId, "cutover batch rejudge"),
                currentTrace(), submissionIds, notifyUsers));
        if (result == null || !result.success()) {
            throw mapError(result);
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

    private String currentActorId() {
        String actorId = currentUserProvider.getCurrentUserId();
        if (actorId == null || actorId.isBlank()) {
            throw new BusinessException(AdminErrorCode.UNAUTHORIZED,
                    "Authenticated admin actor is required");
        }
        return actorId;
    }

    private static IdMetadata idempotency(String requestedKey) {
        String key = requestedKey == null || requestedKey.isBlank()
                ? UUID.randomUUID().toString() : requestedKey.trim();
        if (key.length() > 120) {
            throw new BusinessException(AdminErrorCode.BAD_REQUEST,
                    "Idempotency-Key must not exceed 120 characters");
        }
        return IdMetadata.of(key, null);
    }

    private static String commandId(String operation, IdMetadata idempotency) {
        return UUID.nameUUIDFromBytes(
                (operation + ":" + idempotency.idempotencyKey()).getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    private static TraceMetadata currentTrace() {
        String requestId = TraceIdUtil.current();
        if (requestId == null || requestId.isBlank()) {
            requestId = "t-" + UUID.randomUUID();
        }
        return new TraceMetadata(requestId, null, null, null);
    }

    private static BusinessException mapError(RpcResult<?> result) {
        if (result == null || result.error() == null) {
            return new BusinessException(AdminErrorCode.UNKNOWN_ERROR,
                    "RPC failed without error payload");
        }
        int code = result.error().code();
        if (code == AppErrorCode.CONTENT_NOT_FOUND.code()) {
            return new BusinessException(AdminErrorCode.SUBMISSION_NOT_FOUND, result.error().message());
        }
        if (code == AppErrorCode.BAD_REQUEST.code()) {
            return new BusinessException(AdminErrorCode.BAD_REQUEST, result.error().message());
        }
        if (code == AppErrorCode.UNAUTHORIZED.code()) {
            return new BusinessException(AdminErrorCode.UNAUTHORIZED, result.error().message());
        }
        if (code == AppErrorCode.FORBIDDEN.code()) {
            return new BusinessException(AdminErrorCode.FORBIDDEN, result.error().message());
        }
        if (code == AppErrorCode.VERSION_CONFLICT.code()
                || code == AppErrorCode.CONTENT_STATE_CONFLICT.code()
                || code == AppErrorCode.IDEMPOTENCY_KEY_CONFLICT.code()) {
            return new BusinessException(AdminErrorCode.CONFLICT, result.error().message());
        }
        return new BusinessException(AdminErrorCode.UNKNOWN_ERROR, result.error().message());
    }
}
