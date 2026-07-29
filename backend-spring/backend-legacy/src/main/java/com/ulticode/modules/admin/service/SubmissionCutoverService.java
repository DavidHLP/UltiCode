package com.ulticode.modules.admin.service;

import com.ulticode.app.api.command.ActorDelegation;
import com.ulticode.app.api.command.BatchRejudgeCommand;
import com.ulticode.app.api.command.RejudgeCommand;
import com.ulticode.app.api.dto.BatchRejudgeResultDTO;
import com.ulticode.app.api.dto.RejudgeResultDTO;
import com.ulticode.app.api.service.SubmissionAdministrationService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.modules.submission.dto.BatchRejudgeResponse;
import com.ulticode.modules.submission.dto.RejudgeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * P4-CUTOVER-002: feature-flagged routing adapter for submission rejudge.
 *
 * <p>When {@code app.features.submission-dubbo-cutover=false} (default),
 * delegates directly to {@link AdminSubmissionService#rejudge}. When the
 * flag is {@code true}, the rejudge goes through the Dubbo
 * {@link SubmissionAdministrationService} Provider.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionCutoverService {

    private final AdminSubmissionService submissionService;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = 3000, retries = 0, check = false)
    private SubmissionAdministrationService dubboProvider;

    @Value("${app.features.submission-dubbo-cutover:false}")
    private boolean dubboEnabled;

    public RejudgeResult rejudge(String id, boolean notifyUser) {
        if (!dubboEnabled) {
            return submissionService.rejudge(id, notifyUser);
        }
        RpcResult<RejudgeResultDTO> result = dubboProvider.rejudge(
                new RejudgeCommand(
                        UUID.randomUUID().toString(), IdMetadata.mint(),
                        new ActorDelegation("ADMIN", "admin", "admin", "cutover rejudge"),
                        TraceMetadata.EMPTY, id, notifyUser));
        if (!result.success()) {
            throw mapError(result);
        }
        RejudgeResultDTO dto = result.data();
        RejudgeResult mapped = new RejudgeResult();
        mapped.setSubmissionId(dto.submissionId());
        mapped.setSuccess(true);
        mapped.setNewStatus(dto.newStatus());
        mapped.setRejudgedAt(Instant.ofEpochMilli(dto.rejudgedAtEpochMs()));
        mapped.setRetryCount(dto.retryCount());
        return mapped;
    }

    public BatchRejudgeResponse batchRejudge(List<String> submissionIds, boolean notifyUsers) {
        if (!dubboEnabled) {
            return submissionService.batchRejudge(submissionIds, notifyUsers);
        }
        RpcResult<BatchRejudgeResultDTO> result = dubboProvider.batchRejudge(
                new BatchRejudgeCommand(
                        UUID.randomUUID().toString(), IdMetadata.mint(),
                        new ActorDelegation("ADMIN", "admin", "admin", "cutover batch rejudge"),
                        TraceMetadata.EMPTY, submissionIds, notifyUsers));
        if (!result.success()) {
            throw mapError(result);
        }
        BatchRejudgeResultDTO dto = result.data();
        BatchRejudgeResponse response = new BatchRejudgeResponse();
        response.setTotal(dto.total());
        response.setSuccessful(dto.successful());
        response.setFailed(dto.failed());
        List<RejudgeResult> results = new ArrayList<>(dto.results().size());
        for (RejudgeResultDTO r : dto.results()) {
            RejudgeResult mapped = new RejudgeResult();
            mapped.setSubmissionId(r.submissionId());
            mapped.setSuccess(true);
            mapped.setNewStatus(r.newStatus());
            mapped.setRejudgedAt(Instant.ofEpochMilli(r.rejudgedAtEpochMs()));
            mapped.setRetryCount(r.retryCount());
            results.add(mapped);
        }
        response.setResults(results);
        return response;
    }

    private static BusinessException mapError(RpcResult<?> result) {
        var err = result.error();
        if (err == null) {
            return new BusinessException(ErrorCode.UNKNOWN_ERROR, "RPC failed without error payload");
        }
        int code = err.code();
        if (code == 40401) {
            return new BusinessException(ErrorCode.PROBLEM_NOT_FOUND, err.message());
        }
        if (code == 40901 || code == 40902) {
            return new BusinessException(ErrorCode.CONFLICT, err.message());
        }
        return new BusinessException(ErrorCode.UNKNOWN_ERROR, err.message());
    }
}
