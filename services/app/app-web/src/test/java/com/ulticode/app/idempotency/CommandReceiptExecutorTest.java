package com.ulticode.app.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.app.api.command.ActorDelegation;
import com.ulticode.app.api.command.RejudgeCommand;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.idempotency.entity.AppCommandReceiptEntity;
import com.ulticode.app.idempotency.mapper.AppCommandReceiptMapper;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommandReceiptExecutorTest {

    @Mock
    private AppCommandReceiptMapper receiptMapper;

    private CommandReceiptExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new CommandReceiptExecutor(
                receiptMapper,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void claimsBeforeMutationAndFinalizesSuccessfulResult() {
        when(receiptMapper.insertClaim(any())).thenReturn(1);
        when(receiptMapper.markSuccess(anyString(), eq("\"ok\""))).thenReturn(1);
        RejudgeCommand command = command("key-1");

        RpcResult<String> result = executor.execute(
                "rejudge", command, String.class,
                traceId -> RpcResult.success("ok", traceId));

        assertThat(result.success()).isTrue();
        InOrder order = inOrder(receiptMapper);
        order.verify(receiptMapper).insertClaim(any());
        order.verify(receiptMapper).markSuccess(anyString(), eq("\"ok\""));
    }

    @Test
    void replaysExistingSuccessWithoutRunningMutation() {
        RejudgeCommand command = command("key-2");
        AppCommandReceiptEntity existing = new AppCommandReceiptEntity();
        existing.setStatus("SUCCESS");
        existing.setRequestFingerprint(CommandReceiptExecutor.fingerprint(command));
        existing.setResultPayload("\"replayed\"");
        when(receiptMapper.insertClaim(any())).thenReturn(0);
        when(receiptMapper.findByReceiptKey("SubmissionAdministrationService", "rejudge", "key-2"))
                .thenReturn(existing);

        RpcResult<String> result = executor.execute(
                "rejudge", command, String.class,
                traceId -> {
                    throw new AssertionError("replay must not mutate");
                });

        assertThat(result.data()).isEqualTo("replayed");
        verify(receiptMapper, never()).markSuccess(anyString(), anyString());
    }

    @Test
    void replaysVoidSuccessWithoutRequiringNullPayload() {
        RejudgeCommand command = command("key-void");
        AppCommandReceiptEntity existing = new AppCommandReceiptEntity();
        existing.setStatus("SUCCESS");
        existing.setRequestFingerprint(CommandReceiptExecutor.fingerprint(command));
        existing.setResultPayload("null");
        when(receiptMapper.insertClaim(any())).thenReturn(0);
        when(receiptMapper.findByReceiptKey("SubmissionAdministrationService", "delete", "key-void"))
                .thenReturn(existing);

        RpcResult<Void> result = executor.execute(
                "delete", command, Void.class,
                traceId -> {
                    throw new AssertionError("void replay must not mutate");
                });

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isNull();
        verify(receiptMapper, never()).markSuccess(anyString(), anyString());
    }

    @Test
    void removesClaimWhenMutationReturnsFailure() {
        when(receiptMapper.insertClaim(any())).thenReturn(1);
        RejudgeCommand command = command("key-3");

        RpcResult<String> result = executor.execute(
                "rejudge", command, String.class,
                traceId -> RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, traceId));

        assertThat(result.success()).isFalse();
        verify(receiptMapper).deleteClaim(anyString());
        verify(receiptMapper, never()).markSuccess(anyString(), anyString());
    }

    private static RejudgeCommand command(String key) {
        return new RejudgeCommand(
                "cmd-" + key,
                IdMetadata.of(key, null),
                new ActorDelegation("ADMIN", "admin-1", "admin-1", "rejudge"),
                new TraceMetadata("t-1", null, null, null),
                "submission-1",
                false);
    }
}
