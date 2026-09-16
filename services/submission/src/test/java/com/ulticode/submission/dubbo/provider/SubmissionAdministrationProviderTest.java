package com.ulticode.submission.dubbo.provider;

import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.submission.admin.RejudgeOutcome;
import com.ulticode.submission.admin.SubmissionRejudgeService;
import com.ulticode.submission.api.command.BatchRejudgeCommand;
import com.ulticode.submission.api.command.RejudgeCommand;
import com.ulticode.submission.api.dto.BatchRejudgeResultDTO;
import com.ulticode.submission.api.dto.RejudgeResultDTO;
import com.ulticode.submission.idempotency.SubmissionCommandReceiptExecutor;
import com.ulticode.submission.security.InternalDelegationAssertionVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionAdministrationProviderTest {

    @Mock private SubmissionRejudgeService rejudgeService;
    @Mock private SubmissionCommandReceiptExecutor receiptExecutor;
    @Mock private InternalDelegationAssertionVerifier delegationVerifier;

    @Test
    void rejectsUntrustedActorBeforeDurableReceipt() {
        SubmissionAdministrationProvider provider = provider();
        RejudgeCommand command = command();

        RpcResult<RejudgeResultDTO> result = provider.rejudge(command);

        assertThat(result.success()).isFalse();
        verifyNoInteractions(receiptExecutor, rejudgeService);
    }

    @Test
    void initiatedOutcomeBuildsSuccessfulWireDto() {
        SubmissionAdministrationProvider provider = provider();
        RejudgeCommand command = command();
        RejudgeOutcome outcome = new RejudgeOutcome.Initiated("sub-1", "Pending", 1L, 1);
        when(delegationVerifier.isTrusted(command.actor())).thenReturn(true);
        when(rejudgeService.rejudge("sub-1")).thenReturn(outcome);
        executeSingleMutation(command);

        RpcResult<RejudgeResultDTO> result = provider.rejudge(command);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEqualTo(new RejudgeResultDTO(
                "sub-1", "Pending", 1L, 1, true, null, null));
        assertThat(result.error()).isNull();
        verify(receiptExecutor).execute(
                eq("rejudge"), eq(command), eq(RejudgeResultDTO.class), any());
    }

    @Test
    void rejectedOutcomesMapOwnerCodesToRpcErrors() {
        when(delegationVerifier.isTrusted(command().actor())).thenReturn(true);
        for (RejudgeOutcome.Rejected outcome : List.of(
                new RejudgeOutcome.Rejected(AppErrorCode.CONTENT_NOT_FOUND, "missing"),
                new RejudgeOutcome.Rejected(AppErrorCode.VERSION_CONFLICT, "stale"),
                new RejudgeOutcome.Rejected(AppErrorCode.CONTENT_STATE_CONFLICT, "pending"))) {
            RejudgeCommand command = command();
            when(rejudgeService.rejudge("sub-1")).thenReturn(outcome);
            executeSingleMutation(command);

            RpcResult<RejudgeResultDTO> result = provider().rejudge(command);

            assertThat(result.success()).isFalse();
            assertThat(result.data()).isNull();
            assertThat(result.error().namespace()).isEqualTo(AppErrorCode.NAMESPACE);
            assertThat(result.error().code()).isEqualTo(outcome.code().code());
            assertThat(result.error().message()).isEqualTo(outcome.message());
        }
    }

    @Test
    void batchCountsInitiatedOutcomesAndPreservesRejectedDtoShape() {
        BatchRejudgeCommand command = batchCommand();
        when(delegationVerifier.isTrusted(command.actor())).thenReturn(true);
        RejudgeOutcome.Initiated initiated =
                new RejudgeOutcome.Initiated("sub-1", "Pending", 1L, 1);
        RejudgeOutcome.Rejected notFound =
                new RejudgeOutcome.Rejected(AppErrorCode.CONTENT_NOT_FOUND, "missing");
        RejudgeOutcome.Rejected stateConflict =
                new RejudgeOutcome.Rejected(AppErrorCode.CONTENT_STATE_CONFLICT, "pending");
        when(rejudgeService.rejudge("sub-1")).thenReturn(initiated);
        when(rejudgeService.rejudge("sub-2")).thenReturn(notFound);
        when(rejudgeService.rejudge("sub-3")).thenReturn(stateConflict);
        executeBatchMutation(command);

        RpcResult<BatchRejudgeResultDTO> result = provider().batchRejudge(command);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEqualTo(new BatchRejudgeResultDTO(
                3,
                1,
                2,
                List.of(
                        new RejudgeResultDTO("sub-1", "Pending", 1L, 1, true, null, null),
                        new RejudgeResultDTO(
                                "sub-2", null, 0L, 0, false,
                                AppErrorCode.CONTENT_NOT_FOUND.code(), "missing"),
                        new RejudgeResultDTO(
                                "sub-3", null, 0L, 0, false,
                                AppErrorCode.CONTENT_STATE_CONFLICT.code(), "pending"))));
    }

    @SuppressWarnings("unchecked")
    private void executeSingleMutation(RejudgeCommand command) {
        when(receiptExecutor.execute(
                eq("rejudge"), eq(command), eq(RejudgeResultDTO.class), any()))
                .thenAnswer(invocation -> {
                    Function<String, RpcResult<RejudgeResultDTO>> mutation =
                            invocation.getArgument(3);
                    return mutation.apply("trace-1");
                });
    }

    @SuppressWarnings("unchecked")
    private void executeBatchMutation(BatchRejudgeCommand command) {
        when(receiptExecutor.execute(
                eq("batchRejudge"), eq(command), eq(BatchRejudgeResultDTO.class), any()))
                .thenAnswer(invocation -> {
                    Function<String, RpcResult<BatchRejudgeResultDTO>> mutation =
                            invocation.getArgument(3);
                    return mutation.apply("trace-1");
                });
    }

    private SubmissionAdministrationProvider provider() {
        return new SubmissionAdministrationProvider(
                rejudgeService, receiptExecutor, delegationVerifier);
    }

    private static RejudgeCommand command() {
        return new RejudgeCommand(
                "command-1",
                IdMetadata.of("key-1", null),
                new ActorDelegation("ADMIN", "admin-1", "admin-1", "rejudge"),
                new TraceMetadata("trace-1", null, null, null),
                "sub-1",
                false);
    }

    private static BatchRejudgeCommand batchCommand() {
        return new BatchRejudgeCommand(
                "command-2",
                IdMetadata.of("key-2", null),
                new ActorDelegation("ADMIN", "admin-1", "admin-1", "batchRejudge"),
                new TraceMetadata("trace-1", null, null, null),
                List.of("sub-1", "sub-2", "sub-3"),
                false);
    }
}
