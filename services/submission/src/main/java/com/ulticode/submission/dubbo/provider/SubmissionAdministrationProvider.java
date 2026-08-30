package com.ulticode.submission.dubbo.provider;

import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.submission.admin.SubmissionRejudgeService;
import com.ulticode.submission.api.command.BatchRejudgeCommand;
import com.ulticode.submission.api.command.RejudgeCommand;
import com.ulticode.submission.api.dto.BatchRejudgeResultDTO;
import com.ulticode.submission.api.dto.RejudgeResultDTO;
import com.ulticode.submission.api.service.SubmissionAdministrationService;
import com.ulticode.submission.idempotency.SubmissionCommandReceiptExecutor;
import com.ulticode.submission.security.InternalDelegationAssertionVerifier;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.context.annotation.Profile;

import java.util.ArrayList;
import java.util.List;

/** Trusted, idempotent Admin rejudge boundary owned by backend-submission. */
@DubboService(group = "backend-submission", version = "1.0.0")
@Profile("!test")
@RequiredArgsConstructor
public class SubmissionAdministrationProvider implements SubmissionAdministrationService {

    private final SubmissionRejudgeService rejudgeService;
    private final SubmissionCommandReceiptExecutor receiptExecutor;
    private final InternalDelegationAssertionVerifier delegationVerifier;

    @Override
    public RpcResult<RejudgeResultDTO> rejudge(RejudgeCommand command) {
        String traceId = SubmissionCommandReceiptExecutor.traceId(command);
        if (command == null || !delegationVerifier.isTrusted(command.actor())) {
            return RpcResult.failure(AppErrorCode.FORBIDDEN, traceId);
        }
        return receiptExecutor.execute(
                "rejudge",
                command,
                RejudgeResultDTO.class,
                ignored -> toRpcResult(rejudgeService.rejudge(command.submissionId()), traceId));
    }

    @Override
    public RpcResult<BatchRejudgeResultDTO> batchRejudge(BatchRejudgeCommand command) {
        String traceId = SubmissionCommandReceiptExecutor.traceId(command);
        if (command == null || !delegationVerifier.isTrusted(command.actor())) {
            return RpcResult.failure(AppErrorCode.FORBIDDEN, traceId);
        }
        return receiptExecutor.execute(
                "batchRejudge",
                command,
                BatchRejudgeResultDTO.class,
                ignored -> RpcResult.success(executeBatch(command), traceId));
    }

    private BatchRejudgeResultDTO executeBatch(BatchRejudgeCommand command) {
        List<RejudgeResultDTO> results = new ArrayList<>(command.submissionIds().size());
        int successful = 0;
        for (String submissionId : command.submissionIds()) {
            RejudgeResultDTO result = rejudgeService.rejudge(submissionId);
            results.add(result);
            if (!Boolean.FALSE.equals(result.success())) {
                successful++;
            }
        }
        return new BatchRejudgeResultDTO(
                results.size(), successful, results.size() - successful, List.copyOf(results));
    }

    private static RpcResult<RejudgeResultDTO> toRpcResult(
            RejudgeResultDTO result, String traceId) {
        if (result == null) {
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        }
        if (!Boolean.FALSE.equals(result.success())) {
            return RpcResult.success(result, traceId);
        }
        AppErrorCode code = switch (result.errorCode() == null ? -1 : result.errorCode()) {
            case 40401, 40400, 30001, 30002 -> AppErrorCode.CONTENT_NOT_FOUND;
            case 40901 -> AppErrorCode.VERSION_CONFLICT;
            case 40902, 40900 -> AppErrorCode.CONTENT_STATE_CONFLICT;
            default -> AppErrorCode.UNEXPECTED_APP_STATE;
        };
        return RpcResult.failure(
                new RpcResult.ErrorPayload(AppErrorCode.NAMESPACE, code.code(), result.error()),
                traceId);
    }
}
