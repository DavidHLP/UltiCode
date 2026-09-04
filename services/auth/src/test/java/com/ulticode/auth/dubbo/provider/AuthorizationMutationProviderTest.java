package com.ulticode.auth.dubbo.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.auth.api.command.ActorDelegation;
import com.ulticode.auth.api.command.PermissionMutationCommand;
import com.ulticode.auth.api.dto.AuthorizationMutationDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.authorization.AuthorizationMutationWorkflow;
import com.ulticode.auth.idempotency.CommandReceiptExecutor;
import com.ulticode.auth.idempotency.entity.AuthCommandReceiptEntity;
import com.ulticode.auth.idempotency.mapper.AuthCommandReceiptMapper;
import com.ulticode.auth.security.InternalDelegationAssertionVerifier;
import com.ulticode.auth.security.ProviderActorTrustGate;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorizationMutationProviderTest {

    private AuthCommandReceiptMapper receiptMapper;
    private AuthorizationMutationWorkflow workflow;
    private AuthorizationMutationProvider provider;

    @BeforeEach
    void setUp() {
        receiptMapper = mock(AuthCommandReceiptMapper.class);
        workflow = mock(AuthorizationMutationWorkflow.class);
        InternalDelegationAssertionVerifier verifier = mock(InternalDelegationAssertionVerifier.class);
        when(verifier.isTrusted(any())).thenReturn(true);
        CommandReceiptExecutor receiptExecutor = new CommandReceiptExecutor(
                receiptMapper,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        provider = new AuthorizationMutationProvider(
                workflow, new ProviderActorTrustGate(verifier), receiptExecutor);
    }

    @Test
    void sameIdempotencyKeyReplaysWithoutCallingMutation() throws Exception {
        PermissionMutationCommand command = command(null);
        AuthorizationMutationDTO data = new AuthorizationMutationDTO(
                "user-1", "GRANT", "READ", "PROBLEM", "direct", null, 5L, true);
        AuthCommandReceiptEntity receipt = receipt(command, data);
        when(receiptMapper.findByReceiptKey(
                "AuthorizationMutationService", "mutatePermission", "key-1"))
                .thenReturn(receipt);

        RpcResult<AuthorizationMutationDTO> result = provider.mutatePermission(command);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEqualTo(data);
        verify(workflow, never()).mutatePermission(any());
    }

    @Test
    void sameIdempotencyKeyWithDifferentExpiryConflicts() throws Exception {
        PermissionMutationCommand original = command(null);
        AuthorizationMutationDTO data = new AuthorizationMutationDTO(
                "user-1", "GRANT", "READ", "PROBLEM", "direct", null, 5L, true);
        when(receiptMapper.findByReceiptKey(
                "AuthorizationMutationService", "mutatePermission", "key-1"))
                .thenReturn(receipt(original, data));

        RpcResult<AuthorizationMutationDTO> result = provider.mutatePermission(
                command(LocalDateTime.of(2026, 12, 31, 23, 59)));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AuthErrorCode.IDEMPOTENCY_KEY_CONFLICT.code());
        verify(workflow, never()).mutatePermission(any());
    }

    @Test
    void businessFailureKeepsSpecificErrorCode() {
        PermissionMutationCommand command = command(null);
        when(workflow.mutatePermission(command)).thenThrow(
                new AuthBusinessException(
                        AuthErrorCode.AUTHORIZATION_VERSION_CONFLICT,
                        "stale authorization version",
                        "trace-business"));

        RpcResult<AuthorizationMutationDTO> result = provider.mutatePermission(command);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code())
                .isEqualTo(AuthErrorCode.AUTHORIZATION_VERSION_CONFLICT.code());
        assertThat(result.traceId()).isEqualTo("trace-1");
    }

    private static PermissionMutationCommand command(LocalDateTime expiresAt) {
        OffsetDateTime expiresAtWire = expiresAt == null
                ? null : expiresAt.atOffset(ZoneOffset.UTC);
        return new PermissionMutationCommand(
                "cmd-1", IdMetadata.of("key-1", null),
                new ActorDelegation("ADMIN", "admin-1", "admin-1", "test"),
                new TraceMetadata("trace-1", null, null, null),
                "user-1", PermissionMutationCommand.Operation.GRANT,
                "READ", "PROBLEM", expiresAtWire, 4L, "test");
    }

    private static AuthCommandReceiptEntity receipt(
            PermissionMutationCommand command, AuthorizationMutationDTO data) throws Exception {
        AuthCommandReceiptEntity receipt = new AuthCommandReceiptEntity();
        receipt.setStatus("SUCCESS");
        receipt.setRequestFingerprint(CommandReceiptExecutor.fingerprint(command));
        receipt.setResultPayload(new ObjectMapper().writeValueAsString(data));
        return receipt;
    }
}
