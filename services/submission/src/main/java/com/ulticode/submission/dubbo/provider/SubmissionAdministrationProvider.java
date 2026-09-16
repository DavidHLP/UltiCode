package com.ulticode.submission.dubbo.provider;

import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.submission.admin.RejudgeOutcome;
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
                ignored -> toRpcResult(
                        command.submissionId(),
                        rejudgeService.rejudge(command.submissionId()),
                        traceId));
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
            RejudgeOutcome outcome = rejudgeService.rejudge(submissionId);
            results.add(toDTO(submissionId, outcome));
            if (outcome instanceof RejudgeOutcome.Initiated) {
                successful++;
            }
        }
        return new BatchRejudgeResultDTO(
                results.size(), successful, results.size() - successful, List.copyOf(results));
    }

    private static RpcResult<RejudgeResultDTO> toRpcResult(
            String submissionId, RejudgeOutcome outcome, String traceId) {
        if (outcome == null) {
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        }
        RejudgeResultDTO result = toDTO(submissionId, outcome);
        if (outcome instanceof RejudgeOutcome.Initiated) {
            return RpcResult.success(result, traceId);
        }
        RejudgeOutcome.Rejected rejected = (RejudgeOutcome.Rejected) outcome;
        return RpcResult.failure(
                new RpcResult.ErrorPayload(
                        AppErrorCode.NAMESPACE, rejected.code().code(), rejected.message()),
                traceId);
    }

    private static RejudgeResultDTO toDTO(
            String submissionId, RejudgeOutcome outcome) {
        if (outcome instanceof RejudgeOutcome.Initiated initiated) {
            return new RejudgeResultDTO(
                    initiated.submissionId(),
                    initiated.newStatus(),
                    initiated.rejudgedAtEpochMs(),
                    initiated.retryCount(),
                    true,
                    null,
                    null);
        }
        RejudgeOutcome.Rejected rejected = (RejudgeOutcome.Rejected) outcome;
        return new RejudgeResultDTO(
                submissionId,
                null,
                0L,
                0,
                false,
                rejected.code().code(),
                rejected.message());
    }
}
