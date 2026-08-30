package com.ulticode.submission.dubbo.provider;

import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.submission.admin.SubmissionRejudgeService;
import com.ulticode.submission.api.command.RejudgeCommand;
import com.ulticode.submission.api.dto.RejudgeResultDTO;
import com.ulticode.submission.idempotency.SubmissionCommandReceiptExecutor;
import com.ulticode.submission.security.InternalDelegationAssertionVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    void trustedActorExecutesThroughDurableReceipt() {
        SubmissionAdministrationProvider provider = provider();
        RejudgeCommand command = command();
        RejudgeResultDTO expected = new RejudgeResultDTO("sub-1", "Pending", 1L, 1);
        when(delegationVerifier.isTrusted(command.actor())).thenReturn(true);
        when(receiptExecutor.execute(
                eq("rejudge"), eq(command), eq(RejudgeResultDTO.class), any()))
                .thenReturn(RpcResult.success(expected, "trace-1"));

        RpcResult<RejudgeResultDTO> result = provider.rejudge(command);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEqualTo(expected);
        verify(receiptExecutor).execute(
                eq("rejudge"), eq(command), eq(RejudgeResultDTO.class), any());
        verify(rejudgeService, never()).rejudge(any());
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
}
