package com.ulticode.app.dubbo.provider;

import com.ulticode.submission.api.command.BatchRejudgeCommand;
import com.ulticode.submission.api.command.RejudgeCommand;
import com.ulticode.submission.api.dto.BatchRejudgeResultDTO;
import com.ulticode.submission.api.dto.RejudgeResultDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.submission.api.service.SubmissionAdministrationService;
import com.ulticode.app.idempotency.CommandReceiptExecutor;
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
 */
@Slf4j
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class SubmissionAdministrationProvider implements SubmissionAdministrationService {


    private final SubmissionAdministrationDomainService domainService;
    private final CommandReceiptExecutor receiptExecutor;

    @Override
    public RpcResult<RejudgeResultDTO> rejudge(RejudgeCommand command) {
        try {
            return receiptExecutor.execute(
                    "rejudge",
                    command,
                    RejudgeResultDTO.class,
                    traceId -> executeRejudge(command, traceId));
        } catch (Exception e) {
            log.error("SubmissionAdministrationProvider.rejudge receipt failure commandId={}",
                    command == null ? null : command.commandId(), e);
            return failure(AppErrorCode.UNEXPECTED_APP_STATE,
                    CommandReceiptExecutor.traceId(command), null);
        }
    }

    @Override
    public RpcResult<BatchRejudgeResultDTO> batchRejudge(BatchRejudgeCommand command) {
        try {
            return receiptExecutor.execute(
                    "batchRejudge",
                    command,
                    BatchRejudgeResultDTO.class,
                    traceId -> executeBatchRejudge(command, traceId));
        } catch (Exception e) {
            log.error("SubmissionAdministrationProvider.batchRejudge receipt failure commandId={}",
                    command == null ? null : command.commandId(), e);
            return failure(AppErrorCode.UNEXPECTED_APP_STATE,
                    CommandReceiptExecutor.traceId(command), null);
        }
    }

    private RpcResult<RejudgeResultDTO> executeRejudge(RejudgeCommand command, String traceId) {
        log.info("SubmissionAdministrationProvider.rejudge id={} notifyUser={} commandId={} actor={}",
                command.submissionId(), command.notifyUser(),
                command.commandId(), command.actor().actorId());
        try {
            RejudgeResult result = domainService.rejudge(
                    command.submissionId(), command.notifyUser());
            if (result == null || !Boolean.TRUE.equals(result.getSuccess())) {
                AppErrorCode code = result == null || result.getErrorCode() == null
                        ? AppErrorCode.UNEXPECTED_APP_STATE
                        : errorFor(result.getErrorCode());
                return failure(code, traceId,
                        safeMessage(code, result == null ? null : result.getError()));
            }
            return RpcResult.success(toDto(result), traceId);
        } catch (BusinessException e) {
            return toFailure(e, traceId);
        } catch (Exception e) {
            log.error("SubmissionAdministrationProvider.rejudge unexpected error id={}",
                    command.submissionId(), e);
            return failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId, null);
        }
    }

    private RpcResult<BatchRejudgeResultDTO> executeBatchRejudge(
            BatchRejudgeCommand command, String traceId) {
        log.info("SubmissionAdministrationProvider.batchRejudge count={} notifyUser={} commandId={} actor={}",
                command.submissionIds().size(), command.notifyUsers(),
                command.commandId(), command.actor().actorId());
        try {
            BatchRejudgeResponse response = domainService.batchRejudge(
                    command.submissionIds(), command.notifyUsers());
            if (response == null) {
                return failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId, null);
            }

            List<RejudgeResultDTO> dtos = new ArrayList<>();
            for (RejudgeResult result : response.getResults() == null
                    ? List.<RejudgeResult>of() : response.getResults()) {
                dtos.add(toDto(result));
            }
            BatchRejudgeResultDTO dto = new BatchRejudgeResultDTO(
                    response.getTotal() != null ? response.getTotal() : 0,
                    response.getSuccessful() != null ? response.getSuccessful() : 0,
                    response.getFailed() != null ? response.getFailed() : 0,
                    dtos);
            return RpcResult.success(dto, traceId);
        } catch (BusinessException e) {
            return toFailure(e, traceId);
        } catch (Exception e) {
            log.error("SubmissionAdministrationProvider.batchRejudge unexpected error", e);
            return failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId, null);
        }
    }

    private static RejudgeResultDTO toDto(RejudgeResult result) {
        if (result == null) {
            return new RejudgeResultDTO(
                    null, "unknown", 0L, 0,
                    false, AppErrorCode.UNEXPECTED_APP_STATE.code(), "Missing rejudge result");
        }
        boolean success = Boolean.TRUE.equals(result.getSuccess());
        long epochMs = success
                ? (result.getRejudgedAt() != null
                        ? result.getRejudgedAt().toEpochMilli() : Instant.now().toEpochMilli())
                : 0L;
        int retryCount = result.getRetryCount() != null ? result.getRetryCount() : 0;
        Integer errorCode = result.getErrorCode();
        if (!success && errorCode == null) {
            errorCode = AppErrorCode.UNEXPECTED_APP_STATE.code();
        }
        String error = result.getError();
        if (!success && errorFor(errorCode) == AppErrorCode.UNEXPECTED_APP_STATE) {
            error = null;
        }
        return new RejudgeResultDTO(
                result.getSubmissionId(),
                result.getNewStatus() != null ? result.getNewStatus() : "unknown",
                epochMs,
                retryCount,
                success,
                errorCode,
                error);
    }
    private static AppErrorCode errorFor(Integer errorCode) {
        if (errorCode == null) {
            return AppErrorCode.UNEXPECTED_APP_STATE;
        }
        return switch (errorCode) {
            case 40000 -> AppErrorCode.BAD_REQUEST;
            case 40100 -> AppErrorCode.UNAUTHORIZED;
            case 40300 -> AppErrorCode.FORBIDDEN;
            case 40401, 40400, 30001, 30002 -> AppErrorCode.CONTENT_NOT_FOUND;
            case 40901 -> AppErrorCode.VERSION_CONFLICT;
            case 40902, 40900 -> AppErrorCode.CONTENT_STATE_CONFLICT;
            case 40903 -> AppErrorCode.IDEMPOTENCY_KEY_CONFLICT;
            case 50001 -> AppErrorCode.UNEXPECTED_APP_STATE;
            default -> AppErrorCode.UNEXPECTED_APP_STATE;
        };
    }

    private static <T> RpcResult<T> toFailure(BusinessException exception, String traceId) {
        if (exception.getErrorCode() == null) {
            return failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId, null);
        }
        AppErrorCode code = errorFor(exception.getErrorCode().code());
        return failure(code, traceId, safeMessage(code, exception.getMessage()));
    }

    private static String safeMessage(AppErrorCode code, String message) {
        return code == AppErrorCode.UNEXPECTED_APP_STATE ? null : message;
    }

    private static <T> RpcResult<T> failure(
            AppErrorCode code, String traceId, String message) {
        String text = message == null || message.isBlank() ? code.message() : message;
        return RpcResult.failure(
                new RpcResult.ErrorPayload(AppErrorCode.NAMESPACE, code.code(), text),
                traceId);
    }
}
