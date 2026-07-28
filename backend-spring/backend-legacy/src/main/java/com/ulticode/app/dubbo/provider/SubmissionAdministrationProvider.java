package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.command.RejudgeCommand;
import com.ulticode.app.api.dto.RejudgeResultDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.SubmissionAdministrationService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.dto.RejudgeResult;
import com.ulticode.modules.admin.service.AdminSubmissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.time.Instant;

/**
 * P4-CUTOVER-002: Dubbo Provider implementation of
 * {@link SubmissionAdministrationService}.
 *
 * <p>Delegates to {@link AdminSubmissionService#rejudge} which routes through
 * the P3-OWNER-001-C {@code RejudgePolicy} (generation-fenced CAS). The
 * Provider maps the RPC contract to the internal domain call.
 *
 * <p>Fence enforcement is server-side via
 * {@code RejudgePolicy.rejudgeFenced}'s atomic {@code bumpGeneration} CAS;
 * the command carries no caller-supplied generation (see ADR-P4-RPC-001).
 */
@Slf4j
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class SubmissionAdministrationProvider implements SubmissionAdministrationService {

    private final AdminSubmissionService submissionService;

    @Override
    public RpcResult<RejudgeResultDTO> rejudge(RejudgeCommand command) {
        log.info("SubmissionAdministrationProvider.rejudge id={} notifyUser={} commandId={} actor={}",
                command.submissionId(), command.notifyUser(),
                command.commandId(), command.actor().actorId());
        try {
            RejudgeResult result = submissionService.rejudge(
                    command.submissionId(), command.notifyUser());

            if (Boolean.FALSE.equals(result.getSuccess())) {
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

    private static <T> RpcResult<T> toFailure(BusinessException e, String traceId) {
        return switch (e.getErrorCode().code()) {
            case 30001, 30002 -> // PROBLEM_NOT_FOUND, SUBMISSION_NOT_FOUND family
                    RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, traceId);
            case 40900 -> // CONFLICT
                    RpcResult.failure(AppErrorCode.CONTENT_STATE_CONFLICT, traceId);
            default -> RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        };
    }
}
