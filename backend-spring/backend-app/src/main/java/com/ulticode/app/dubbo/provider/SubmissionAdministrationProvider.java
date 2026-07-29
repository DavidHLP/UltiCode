package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.command.BatchRejudgeCommand;
import com.ulticode.app.api.command.RejudgeCommand;
import com.ulticode.app.api.dto.BatchRejudgeResultDTO;
import com.ulticode.app.api.dto.RejudgeResultDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.SubmissionAdministrationService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.submission.dto.BatchRejudgeResponse;
import com.ulticode.modules.submission.dto.RejudgeResult;
import com.ulticode.modules.submission.service.SubmissionAdministrationDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Dubbo Provider implementation of {@link SubmissionAdministrationService} in {@code backend-app}.
 *
 * <p>Delegates to {@link SubmissionAdministrationDomainService} for canonical write-side domain logic.
 */
@Slf4j
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class SubmissionAdministrationProvider implements SubmissionAdministrationService {

    private final SubmissionAdministrationDomainService domainService;

    @Override
    public RpcResult<RejudgeResultDTO> rejudge(RejudgeCommand command) {
        log.info("SubmissionAdministrationProvider.rejudge id={} notifyUser={} commandId={} actor={}",
                command.submissionId(), command.notifyUser(),
                command.commandId(), command.actor().actorId());
        try {
            RejudgeResult result = domainService.rejudge(
                    command.submissionId(), command.notifyUser());

            if (result == null || Boolean.FALSE.equals(result.getSuccess())) {
                // AdminSubmissionService returns success=false for not-found
                // rather than throwing; map to CONTENT_NOT_FOUND
                return RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, command.trace().traceId());
            }

            long epochMs = result.getRejudgedAt() != null
                    ? result.getRejudgedAt().toEpochMilli() : Instant.now().toEpochMilli();
            int retryCount = result.getRetryCount() != null ? result.getRetryCount() : 0;
            RejudgeResultDTO dto = new RejudgeResultDTO(
                    result.getSubmissionId(),
                    result.getNewStatus() != null ? result.getNewStatus() : "unknown",
                    epochMs,
                    retryCount);
            return RpcResult.success(dto, command.trace().traceId());
        } catch (BusinessException e) {
            return toFailure(e, command.trace().traceId());
        } catch (Exception e) {
            log.error("SubmissionAdministrationProvider.rejudge unexpected error id={}",
                    command.submissionId(), e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, command.trace().traceId());
        }
    }

    @Override
    public RpcResult<BatchRejudgeResultDTO> batchRejudge(BatchRejudgeCommand command) {
        log.info("SubmissionAdministrationProvider.batchRejudge count={} notifyUser={} commandId={} actor={}",
                command.submissionIds().size(), command.notifyUsers(),
                command.commandId(), command.actor().actorId());
        try {
            BatchRejudgeResponse response = domainService.batchRejudge(
                    command.submissionIds(), command.notifyUsers());

            List<RejudgeResultDTO> dtos = new ArrayList<>(response.getResults().size());
            for (RejudgeResult rr : response.getResults()) {
                long epochMs = rr.getRejudgedAt() != null
                        ? rr.getRejudgedAt().toEpochMilli() : Instant.now().toEpochMilli();
                int retryCount = rr.getRetryCount() != null ? rr.getRetryCount() : 0;
                dtos.add(new RejudgeResultDTO(
                        rr.getSubmissionId(),
                        rr.getNewStatus() != null ? rr.getNewStatus() : "unknown",
                        epochMs,
                        retryCount));
            }
            BatchRejudgeResultDTO dto = new BatchRejudgeResultDTO(
                    response.getTotal() != null ? response.getTotal() : 0,
                    response.getSuccessful() != null ? response.getSuccessful() : 0,
                    response.getFailed() != null ? response.getFailed() : 0,
                    dtos);
            return RpcResult.success(dto, command.trace().traceId());
        } catch (BusinessException e) {
            return toFailure(e, command.trace().traceId());
        } catch (Exception e) {
            log.error("SubmissionAdministrationProvider.batchRejudge unexpected error", e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, command.trace().traceId());
        }
    }

    private static <T> RpcResult<T> toFailure(BusinessException e, String traceId) {
        if (e.getErrorCode() == null) {
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        }
        return switch (e.getErrorCode().code()) {
            case 40400, 30001, 30002 -> // NOT_FOUND, PROBLEM_NOT_FOUND, SUBMISSION_NOT_FOUND family
                    RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, traceId);
            case 40900 -> // CONFLICT
                    RpcResult.failure(AppErrorCode.CONTENT_STATE_CONFLICT, traceId);
            default -> RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        };
    }
}
