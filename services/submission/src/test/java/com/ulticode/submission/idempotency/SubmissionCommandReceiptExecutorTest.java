package com.ulticode.submission.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.submission.api.command.RejudgeCommand;
import com.ulticode.submission.api.dto.RejudgeResultDTO;
import com.ulticode.submission.idempotency.entity.SubmissionCommandReceiptEntity;
import com.ulticode.submission.idempotency.mapper.SubmissionCommandReceiptMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionCommandReceiptExecutorTest {

    @Mock private SubmissionCommandReceiptMapper mapper;

    private ObjectMapper objectMapper;
    private SubmissionCommandReceiptExecutor executor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        executor = new SubmissionCommandReceiptExecutor(
                mapper,
                objectMapper,
                Clock.fixed(Instant.parse("2026-08-30T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void receiptRoundTripPreservesRejudgeDtoWireShape() throws Exception {
        RejudgeCommand command = command("key-1", "sub-1");
        RejudgeResultDTO expected = new RejudgeResultDTO(
                "sub-1", "Pending", 1L, 1, true, null, null);
        when(mapper.insertClaim(any())).thenReturn(1);
        when(mapper.markSuccess(any(), any())).thenReturn(1);

        RpcResult<RejudgeResultDTO> first = executor.execute(
                "rejudge", command, RejudgeResultDTO.class,
                traceId -> RpcResult.success(expected, traceId));

        assertThat(first.success()).isTrue();
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(mapper).markSuccess(any(), payload.capture());
        var stored = objectMapper.readTree(payload.getValue());
        java.util.List<String> fieldNames = new java.util.ArrayList<>();
        stored.fieldNames().forEachRemaining(fieldNames::add);
        assertThat(fieldNames).containsExactlyInAnyOrder(
                "submissionId", "newStatus", "rejudgedAtEpochMs", "retryCount",
                "success", "errorCode", "error");
        assertThat(stored.get("submissionId").asText()).isEqualTo("sub-1");
        assertThat(stored.get("newStatus").asText()).isEqualTo("Pending");
        assertThat(stored.get("rejudgedAtEpochMs").asLong()).isEqualTo(1L);
        assertThat(stored.get("retryCount").asInt()).isEqualTo(1);
        assertThat(stored.get("success").asBoolean()).isTrue();
        assertThat(stored.get("errorCode").isNull()).isTrue();
        assertThat(stored.get("error").isNull()).isTrue();

        SubmissionCommandReceiptEntity receipt = receipt(command, "SUCCESS");
        receipt.setResultPayload(payload.getValue());
        when(mapper.insertClaim(any())).thenReturn(0);
        when(mapper.findByReceiptKey(
                "SubmissionAdministrationService", "rejudge", "key-1"))
                .thenReturn(receipt);
        AtomicInteger replayMutations = new AtomicInteger();

        RpcResult<RejudgeResultDTO> replayed = executor.execute(
                "rejudge", command, RejudgeResultDTO.class,
                traceId -> {
                    replayMutations.incrementAndGet();
                    return RpcResult.success(expected, traceId);
                });

        assertThat(replayed.data()).isEqualTo(expected);
        assertThat(objectMapper.readTree(
                objectMapper.writeValueAsString(replayed.data()))).isEqualTo(stored);
        assertThat(replayMutations).hasValue(0);
    }

    @Test
    void firstCommandClaimsMutatesAndFinalizes() {
        RejudgeCommand command = command("key-1", "sub-1");
        AtomicInteger mutations = new AtomicInteger();
        when(mapper.insertClaim(any())).thenReturn(1);
        when(mapper.markSuccess(any(), any())).thenReturn(1);

        RpcResult<RejudgeResultDTO> result = executor.execute(
                "rejudge", command, RejudgeResultDTO.class,
                traceId -> {
                    mutations.incrementAndGet();
                    return RpcResult.success(success("sub-1"), traceId);
                });

        assertThat(result.success()).isTrue();
        assertThat(result.idempotencyKey()).isEqualTo("key-1");
        assertThat(mutations).hasValue(1);
        verify(mapper).markSuccess(any(), any());
    }

    @Test
    void completedDuplicateReplaysWithoutMutation() throws Exception {
        RejudgeCommand command = command("key-1", "sub-1");
        SubmissionCommandReceiptEntity receipt = receipt(command, "SUCCESS");
        receipt.setResultPayload(objectMapper.writeValueAsString(success("sub-1")));
        when(mapper.insertClaim(any())).thenReturn(0);
        when(mapper.findByReceiptKey(
                "SubmissionAdministrationService", "rejudge", "key-1"))
                .thenReturn(receipt);
        AtomicInteger mutations = new AtomicInteger();

        RpcResult<RejudgeResultDTO> result = executor.execute(
                "rejudge", command, RejudgeResultDTO.class,
                traceId -> {
                    mutations.incrementAndGet();
                    return RpcResult.success(success("sub-1"), traceId);
                });

        assertThat(result.success()).isTrue();
        assertThat(result.data().submissionId()).isEqualTo("sub-1");
        assertThat(mutations).hasValue(0);
    }

    @Test
    void reusedKeyWithDifferentPayloadConflicts() {
        RejudgeCommand command = command("key-1", "sub-2");
        SubmissionCommandReceiptEntity receipt = receipt(command("key-1", "sub-1"), "SUCCESS");
        when(mapper.insertClaim(any())).thenReturn(0);
        when(mapper.findByReceiptKey(
                "SubmissionAdministrationService", "rejudge", "key-1"))
                .thenReturn(receipt);

        RpcResult<RejudgeResultDTO> result = executor.execute(
                "rejudge", command, RejudgeResultDTO.class,
                traceId -> RpcResult.success(success("sub-2"), traceId));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.IDEMPOTENCY_KEY_CONFLICT.code());
    }

    @Test
    void concurrentProcessingDuplicateDoesNotMutate() {
        RejudgeCommand command = command("key-1", "sub-1");
        when(mapper.insertClaim(any())).thenReturn(0);
        when(mapper.findByReceiptKey(
                "SubmissionAdministrationService", "rejudge", "key-1"))
                .thenReturn(receipt(command, "PROCESSING"));
        AtomicInteger mutations = new AtomicInteger();

        RpcResult<RejudgeResultDTO> result = executor.execute(
                "rejudge", command, RejudgeResultDTO.class,
                traceId -> {
                    mutations.incrementAndGet();
                    return RpcResult.success(success("sub-1"), traceId);
                });

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.VERSION_CONFLICT.code());
        assertThat(mutations).hasValue(0);
    }

    @Test
    void failedMutationReleasesClaimForRetry() {
        RejudgeCommand command = command("key-1", "sub-1");
        when(mapper.insertClaim(any())).thenReturn(1);

        RpcResult<RejudgeResultDTO> result = executor.execute(
                "rejudge", command, RejudgeResultDTO.class,
                traceId -> RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, traceId));

        assertThat(result.success()).isFalse();
        verify(mapper).deleteClaim(any());
    }

    private static RejudgeCommand command(String key, String submissionId) {
        return new RejudgeCommand(
                "command-1",
                IdMetadata.of(key, null),
                new ActorDelegation("ADMIN", "admin-1", "admin-1", "rejudge"),
                new TraceMetadata("trace-1", null, null, null),
                submissionId,
                false);
    }

    private static RejudgeResultDTO success(String submissionId) {
        return new RejudgeResultDTO(submissionId, "Pending", 1L, 1);
    }

    private static SubmissionCommandReceiptEntity receipt(
            RejudgeCommand command, String status) {
        SubmissionCommandReceiptEntity receipt = new SubmissionCommandReceiptEntity();
        receipt.setId("receipt-1");
        receipt.setService("SubmissionAdministrationService");
        receipt.setOperation("rejudge");
        receipt.setIdempotencyKey(command.idempotency().idempotencyKey());
        receipt.setRequestFingerprint(SubmissionCommandReceiptExecutor.fingerprint(command));
        receipt.setStatus(status);
        return receipt;
    }
}
