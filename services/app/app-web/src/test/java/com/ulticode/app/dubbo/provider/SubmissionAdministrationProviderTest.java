package com.ulticode.app.dubbo.provider;

import com.ulticode.common.command.ActorDelegation;
import com.ulticode.submission.api.command.BatchRejudgeCommand;
import com.ulticode.submission.api.command.RejudgeCommand;
import com.ulticode.submission.api.dto.BatchRejudgeResultDTO;
import com.ulticode.submission.api.dto.RejudgeResultDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.idempotency.CommandReceiptExecutor;
import com.ulticode.app.idempotency.mapper.AppCommandReceiptMapper;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.modules.submission.dto.BatchRejudgeResponse;
import com.ulticode.modules.submission.dto.RejudgeResult;
import com.ulticode.modules.submission.service.SubmissionAdministrationDomainService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
@DisplayName("SubmissionAdministrationProvider")
class SubmissionAdministrationProviderTest {

    @Mock private SubmissionAdministrationDomainService domainService;
    @Mock private AppCommandReceiptMapper receiptMapper;
    private SubmissionAdministrationProvider provider;

    @BeforeEach
    void setUp() {
        when(receiptMapper.insertClaim(any())).thenReturn(1);
        lenient().when(receiptMapper.markSuccess(anyString(), anyString())).thenReturn(1);
        CommandReceiptExecutor receiptExecutor =
                new CommandReceiptExecutor(receiptMapper, new ObjectMapper(), Clock.systemUTC());
        provider = new SubmissionAdministrationProvider(domainService, receiptExecutor);
    }

    private static ActorDelegation actor() {
        return new ActorDelegation("ADMIN", "admin", "admin", "test");
    }

    @Test @DisplayName("rejudge maps domain result to DTO")
    void rejudges() {
        RejudgeResult domainRes = new RejudgeResult();
        domainRes.setSubmissionId("sub-1");
        domainRes.setSuccess(true);
        domainRes.setOldStatus("WRONG_ANSWER");
        domainRes.setNewStatus("PENDING");
        domainRes.setError(null);
        domainRes.setRejudgedAt(Instant.parse("2026-07-29T10:00:00Z"));
        domainRes.setRetryCount(2);

        when(domainService.rejudge("sub-1", true)).thenReturn(domainRes);

        var cmd = new RejudgeCommand(
                UUID.randomUUID().toString(), IdMetadata.mint(),
                actor(), TraceMetadata.EMPTY, "sub-1", true);
        RpcResult<RejudgeResultDTO> result = provider.rejudge(cmd);

        assertThat(result.success()).isTrue();
        assertThat(result.data().submissionId()).isEqualTo("sub-1");
        assertThat(result.data().newStatus()).isEqualTo("PENDING");
        assertThat(result.data().retryCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("unexpected domain failure returns generic RPC error text")
    void hidesUnexpectedDomainMessage() {
        when(domainService.rejudge("sub-1", true))
                .thenThrow(new IllegalStateException("database password leaked"));

        RpcResult<RejudgeResultDTO> result = provider.rejudge(
                new RejudgeCommand(
                        UUID.randomUUID().toString(), IdMetadata.mint(),
                        actor(), TraceMetadata.EMPTY, "sub-1", true));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.UNEXPECTED_APP_STATE.code());
        assertThat(result.error().message()).isEqualTo(AppErrorCode.UNEXPECTED_APP_STATE.message());
    }

    @Test
    @DisplayName("null domain success is reported as an unexpected app state")
    void rejectsMissingDomainSuccess() {
        when(domainService.rejudge("sub-1", true)).thenReturn(new RejudgeResult());

        RpcResult<RejudgeResultDTO> result = provider.rejudge(
                new RejudgeCommand(
                        UUID.randomUUID().toString(), IdMetadata.mint(),
                        actor(), TraceMetadata.EMPTY, "sub-1", true));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.UNEXPECTED_APP_STATE.code());
    }

    @Test @DisplayName("batchRejudge maps domain batch response to DTO")
    void batchRejudges() {
        RejudgeResult r1 = new RejudgeResult();
        r1.setSubmissionId("sub-1");
        r1.setSuccess(true);

        RejudgeResult r2 = new RejudgeResult();
        r2.setSubmissionId("sub-2");
        r2.setSuccess(false);
        r2.setError("Submission not found");

        BatchRejudgeResponse domainBatch = new BatchRejudgeResponse();
        domainBatch.setTotal(2);
        domainBatch.setSuccessful(1);
        domainBatch.setFailed(1);
        domainBatch.setResults(List.of(r1, r2));

        when(domainService.batchRejudge(List.of("sub-1", "sub-2"), true)).thenReturn(domainBatch);

        var cmd = new BatchRejudgeCommand(
                UUID.randomUUID().toString(), IdMetadata.mint(),
                actor(), TraceMetadata.EMPTY, List.of("sub-1", "sub-2"), true);
        RpcResult<BatchRejudgeResultDTO> result = provider.batchRejudge(cmd);

        assertThat(result.success()).isTrue();
        assertThat(result.data().total()).isEqualTo(2);
        assertThat(result.data().successful()).isEqualTo(1);
        assertThat(result.data().failed()).isEqualTo(1);
        assertThat(result.data().results()).hasSize(2);
        assertThat(result.data().results().get(0).submissionId()).isEqualTo("sub-1");
    }

    @Test
    @DisplayName("same idempotency key replays the stored result without a second mutation")
    void replaysSameKey() throws Exception {
        String key = "rejudge-key-1";
        RejudgeResult domainRes = new RejudgeResult();
        domainRes.setSubmissionId("sub-1");
        domainRes.setSuccess(true);
        domainRes.setNewStatus("PENDING");
        domainRes.setRejudgedAt(Instant.parse("2026-07-29T10:00:00Z"));
        domainRes.setRetryCount(2);
        when(domainService.rejudge("sub-1", true)).thenReturn(domainRes);
        when(receiptMapper.insertClaim(any())).thenReturn(1, 0);

        RejudgeCommand first = command(key, "cmd-a", true, "t-1");
        RejudgeCommand retry = command(key, "cmd-b", true, "t-2");
        RpcResult<RejudgeResultDTO> firstResult = provider.rejudge(first);
        var receipt = org.mockito.ArgumentCaptor.forClass(
                com.ulticode.app.idempotency.entity.AppCommandReceiptEntity.class);
        verify(receiptMapper).insertClaim(receipt.capture());
        receipt.getValue().setStatus("SUCCESS");
        receipt.getValue().setResultPayload(new ObjectMapper().writeValueAsString(firstResult.data()));
        when(receiptMapper.findByReceiptKey(
                "SubmissionAdministrationService", "rejudge", key))
                .thenReturn(receipt.getValue());

        RpcResult<RejudgeResultDTO> replay = provider.rejudge(retry);

        assertThat(firstResult.success()).isTrue();
        assertThat(replay.success()).isTrue();
        assertThat(replay.idempotencyKey()).isEqualTo(key);
        assertThat(replay.data()).isEqualTo(firstResult.data());
        verify(domainService, times(1)).rejudge("sub-1", true);
    }

    @Test
    @DisplayName("same idempotency key with different request returns a conflict")
    void rejectsFingerprintMismatch() {
        String key = "rejudge-key-2";
        RejudgeResult domainRes = new RejudgeResult();
        domainRes.setSubmissionId("sub-1");
        domainRes.setSuccess(true);
        when(domainService.rejudge("sub-1", true)).thenReturn(domainRes);
        when(receiptMapper.insertClaim(any())).thenReturn(1, 0);

        provider.rejudge(command(key, "cmd-a", true, "t-1"));
        var receipt = org.mockito.ArgumentCaptor.forClass(
                com.ulticode.app.idempotency.entity.AppCommandReceiptEntity.class);
        verify(receiptMapper).insertClaim(receipt.capture());
        when(receiptMapper.findByReceiptKey(
                "SubmissionAdministrationService", "rejudge", key))
                .thenReturn(receipt.getValue());

        RpcResult<RejudgeResultDTO> conflict =
                provider.rejudge(command(key, "cmd-b", false, "t-2"));

        assertThat(conflict.success()).isFalse();
        assertThat(conflict.error().code()).isEqualTo(40903);
        verify(domainService, times(1)).rejudge("sub-1", true);
    }

    private static RejudgeCommand command(
            String key, String commandId, boolean notifyUser, String traceId) {
        return new RejudgeCommand(
                commandId,
                IdMetadata.of(key, null),
                actor(),
                new TraceMetadata(traceId, null, null, null),
                "sub-1",
                notifyUser);
    }
}
