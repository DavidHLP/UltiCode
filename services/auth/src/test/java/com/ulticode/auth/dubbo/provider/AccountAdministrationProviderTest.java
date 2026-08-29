package com.ulticode.auth.dubbo.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.auth.account.AuthAccountPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.api.command.ActorDelegation;
import com.ulticode.auth.api.command.ChangeAccountStateCommand;
import com.ulticode.auth.api.command.ChangeAuthorizationCommand;
import com.ulticode.auth.api.dto.AccountStateDTO;
import com.ulticode.auth.api.dto.AuthorizationSnapshotDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.idempotency.CommandReceiptExecutor;
import com.ulticode.auth.idempotency.entity.AuthCommandReceiptEntity;
import com.ulticode.auth.idempotency.mapper.AuthCommandReceiptMapper;
import com.ulticode.auth.permission.service.PermissionService;
import com.ulticode.auth.service.AccountAdministrationWorkflow;
import com.ulticode.auth.security.InternalDelegationAssertionVerifier;
import com.ulticode.auth.security.ProviderActorTrustGate;
import com.ulticode.auth.service.DefaultAccountAdministrationWorkflow;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountAdministrationProviderTest {

    private AuthAccountPort authAccountPort;
    private PermissionService permissionService;
    private AuthCommandReceiptMapper receiptMapper;
    private InternalDelegationAssertionVerifier delegationVerifier;
    private AccountAdministrationProvider provider;

    private ActorDelegation actor;
    private TraceMetadata trace;

    @BeforeEach
    void setUp() {
        authAccountPort = mock(AuthAccountPort.class);
        permissionService = mock(PermissionService.class);
        receiptMapper = mock(AuthCommandReceiptMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();
        Clock clock = Clock.fixed(Instant.parse("2026-08-03T10:00:00Z"), ZoneOffset.UTC);
        AccountAdministrationWorkflow workflow =
                new DefaultAccountAdministrationWorkflow(authAccountPort, permissionService);
        CommandReceiptExecutor receiptExecutor =
                new CommandReceiptExecutor(receiptMapper, objectMapper, clock);
        delegationVerifier = mock(InternalDelegationAssertionVerifier.class);
        when(delegationVerifier.isTrusted(any())).thenReturn(true);
        provider = new AccountAdministrationProvider(
                workflow, receiptExecutor, new ProviderActorTrustGate(delegationVerifier));

        actor = new ActorDelegation("ADMIN", "admin-1", "org-1", "reason");
        trace = new TraceMetadata("t-123", "span-1", null, null);
    }
    @Test
    @DisplayName("rejects account mutation without a trusted delegation assertion")
    void rejectsUntrustedActor() {
        when(delegationVerifier.isTrusted(any())).thenReturn(false);

        RpcResult<AccountStateDTO> result = provider.changeState(stateCommand(
                "cmd-untrusted", IdMetadata.mint(), "user-1", 1L,
                ChangeAccountStateCommand.AccountStateAction.DISABLE));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(40300);
    }

    @Test
    @DisplayName("changeState succeeds and increments version upon atomic CAS match")
    void changeStateSuccess() {
        AuthAccountRecord record = account("user-1", "USER", true, false, 2L);
        when(authAccountPort.findById("user-1")).thenReturn(Optional.of(record));
        when(authAccountPort.updateAccountIfVersion("user-1", false, false, "USER", 2L))
                .thenReturn(true);

        ChangeAccountStateCommand command = stateCommand(
                "cmd-1", IdMetadata.mint(), "user-1", 2L,
                ChangeAccountStateCommand.AccountStateAction.DISABLE);

        RpcResult<AccountStateDTO> result = provider.changeState(command);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isNotNull();
        assertThat(result.data().accountId()).isEqualTo("user-1");
        assertThat(result.data().active()).isFalse();
        assertThat(result.data().version()).isEqualTo(3L);
        verify(receiptMapper).insert(any(AuthCommandReceiptEntity.class));
    }

    @Test
    @DisplayName("changeState returns ACCOUNT_NOT_FOUND when account is absent")
    void changeStateNotFound() {
        when(authAccountPort.findById("user-99")).thenReturn(Optional.empty());

        RpcResult<AccountStateDTO> result = provider.changeState(
                stateCommand("cmd-1", IdMetadata.mint(), "user-99", 1L,
                        ChangeAccountStateCommand.AccountStateAction.DISABLE));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AuthErrorCode.ACCOUNT_NOT_FOUND.code());
        verify(receiptMapper, never()).insert(any(AuthCommandReceiptEntity.class));
    }

    @Test
    @DisplayName("changeState returns AUTHORIZATION_VERSION_CONFLICT when expected version is stale")
    void changeStateVersionConflict() {
        when(authAccountPort.findById("user-1"))
                .thenReturn(Optional.of(account("user-1", "USER", true, false, 5L)));

        RpcResult<AccountStateDTO> result = provider.changeState(
                stateCommand("cmd-1", IdMetadata.mint(), "user-1", 2L,
                        ChangeAccountStateCommand.AccountStateAction.DISABLE));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code())
                .isEqualTo(AuthErrorCode.AUTHORIZATION_VERSION_CONFLICT.code());
    }

    @Test
    @DisplayName("changeState replays DTO result from receipt on duplicate idempotency key")
    void changeStateIdempotencyReplay() throws Exception {
        IdMetadata idempotency = IdMetadata.of("key-abc", null);
        ChangeAccountStateCommand command = stateCommand(
                "cmd-1", idempotency, "user-1", 2L,
                ChangeAccountStateCommand.AccountStateAction.DISABLE);
        AccountStateDTO cachedDto = new AccountStateDTO("user-1", false, false, 3L);
        AuthCommandReceiptEntity receipt = receipt("SUCCESS", cachedDto, command, "changeState");
        when(receiptMapper.findByReceiptKey("AccountAdministrationService", "changeState", "key-abc"))
                .thenReturn(receipt);

        RpcResult<AccountStateDTO> result = provider.changeState(command);

        assertThat(result.success()).isTrue();
        assertThat(result.data().version()).isEqualTo(3L);
        verify(authAccountPort, never()).updateAccountIfVersion(
                anyString(), anyBoolean(), anyBoolean(), anyString(), anyLong());
    }

    @Test
    @DisplayName("same idempotency key with different business payload returns conflict")
    void changeStateIdempotencyConflict() {
        IdMetadata idempotency = IdMetadata.of("key-abc", null);
        ChangeAccountStateCommand original = stateCommand(
                "cmd-1", idempotency, "user-1", 2L,
                ChangeAccountStateCommand.AccountStateAction.DISABLE);
        AuthCommandReceiptEntity receipt = new AuthCommandReceiptEntity();
        receipt.setStatus("SUCCESS");
        receipt.setRequestFingerprint(CommandReceiptExecutor.fingerprint(original));
        when(receiptMapper.findByReceiptKey("AccountAdministrationService", "changeState", "key-abc"))
                .thenReturn(receipt);

        ChangeAccountStateCommand changed = stateCommand(
                "cmd-2", idempotency, "user-1", 2L,
                ChangeAccountStateCommand.AccountStateAction.BAN);

        RpcResult<AccountStateDTO> result = provider.changeState(changed);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code())
                .isEqualTo(AuthErrorCode.IDEMPOTENCY_KEY_CONFLICT.code());
        verify(authAccountPort, never()).findById(anyString());
    }

    @Test
    @DisplayName("legacy fingerprint replays and versioned fingerprint conflicts")
    void legacyFingerprintCompatibility() {
        IdMetadata idempotency = IdMetadata.of("key-legacy", null);
        ChangeAccountStateCommand original = stateCommand(
                "cmd-legacy", idempotency, "user-1", 2L,
                ChangeAccountStateCommand.AccountStateAction.DISABLE);
        AuthCommandReceiptEntity receipt = new AuthCommandReceiptEntity();
        receipt.setStatus("SUCCESS");
        receipt.setRequestFingerprint(legacyStateFingerprint());
        receipt.setResultPayload("{\"accountId\":\"user-1\",\"active\":false,\"banned\":false,\"version\":3}");
        when(receiptMapper.findByReceiptKey("AccountAdministrationService", "changeState", "key-legacy"))
                .thenReturn(receipt);

        assertThat(provider.changeState(original).success()).isTrue();

        receipt.setRequestFingerprint(CommandReceiptExecutor.fingerprint(original));
        ChangeAccountStateCommand changedVersion = stateCommand(
                "cmd-legacy-2", idempotency, "user-1", 3L,
                ChangeAccountStateCommand.AccountStateAction.DISABLE);
        assertThat(provider.changeState(changedVersion).error().code())
                .isEqualTo(AuthErrorCode.IDEMPOTENCY_KEY_CONFLICT.code());
    }

    private static String legacyStateFingerprint() {
        String payload = String.join("|",
                "6:user-1", "7:DISABLE", "4:test");
        return sha256("com.ulticode.auth.api.command.ChangeAccountStateCommand\u001f" + payload);
    }
    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }


    @Test
    @DisplayName("bootstrap actor cannot change authorization")
    void bootstrapActorCannotChangeAuthorization() {
        ActorDelegation bootstrap = new ActorDelegation(
                "BOOTSTRAP", "bootstrap", "bootstrap", "one-shot");
        ChangeAuthorizationCommand command = new ChangeAuthorizationCommand(
                "cmd-bootstrap-authz", IdMetadata.mint(), bootstrap, trace,
                "user-1", 1L, "ADMIN", Set.of(), "bootstrap scope test");

        RpcResult<AuthorizationSnapshotDTO> result = provider.changeAuthorization(command);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(40300);
        verify(authAccountPort, never()).updateAccountIfVersion(
                anyString(), anyBoolean(), anyBoolean(), anyString(), anyLong());
    }
    @Test
    @DisplayName("changeAuthorization updates role, synchronizes permissions and bumps version")
    void changeAuthorizationSuccess() {
        when(authAccountPort.findById("user-1"))
                .thenReturn(Optional.of(account("user-1", "USER", true, false, 1L)));
        when(authAccountPort.updateAccountIfVersion("user-1", true, false, "ADMIN", 1L))
                .thenReturn(true);
        when(permissionService.getUserPermissions("user-1")).thenReturn(List.of());
        when(permissionService.getUserPermissionStrings("user-1"))
                .thenReturn(List.of("READ:PROBLEM", "WRITE:PROBLEM"));

        ChangeAuthorizationCommand command = new ChangeAuthorizationCommand(
                "cmd-2", IdMetadata.mint(), actor, trace, "user-1", 1L,
                "ADMIN", Set.of("READ:PROBLEM", "WRITE:PROBLEM"), "promote to admin");

        RpcResult<AuthorizationSnapshotDTO> result = provider.changeAuthorization(command);

        assertThat(result.success()).isTrue();
        assertThat(result.data().role()).isEqualTo("ADMIN");
        assertThat(result.data().version()).isEqualTo(2L);
        assertThat(result.data().permissions())
                .containsExactlyInAnyOrder("READ:PROBLEM", "WRITE:PROBLEM");
        verify(receiptMapper).insert(any(AuthCommandReceiptEntity.class));
    }

    @Test
    @DisplayName("receipt insert failure is returned as auth failure")
    void receiptInsertFailure() {
        when(authAccountPort.findById("user-1"))
                .thenReturn(Optional.of(account("user-1", "USER", true, false, 2L)));
        when(authAccountPort.updateAccountIfVersion("user-1", false, false, "USER", 2L))
                .thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("receipt unavailable"))
                .when(receiptMapper).insert(any(AuthCommandReceiptEntity.class));

        RpcResult<AccountStateDTO> result = provider.changeState(
                stateCommand("cmd-1", IdMetadata.mint(), "user-1", 2L,
                        ChangeAccountStateCommand.AccountStateAction.DISABLE));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AuthErrorCode.UNEXPECTED_AUTH_STATE.code());
    }

    private ChangeAccountStateCommand stateCommand(
            String commandId,
            IdMetadata idempotency,
            String accountId,
            long expectedVersion,
            ChangeAccountStateCommand.AccountStateAction action) {
        return new ChangeAccountStateCommand(
                commandId, idempotency, actor, trace, accountId, expectedVersion, action, "test");
    }

    private static AuthAccountRecord account(
            String id, String role, boolean active, boolean banned, long version) {
        return new AuthAccountRecord(
                id, "alice", "alice@example.com", "secret", role, active, banned,
                null, null, version);
    }

    private static <T> AuthCommandReceiptEntity receipt(
            String status, T result, ChangeAccountStateCommand command, String operation)
            throws Exception {
        AuthCommandReceiptEntity receipt = new AuthCommandReceiptEntity();
        receipt.setStatus(status);
        receipt.setRequestFingerprint(CommandReceiptExecutor.fingerprint(command));
        receipt.setResultPayload(new ObjectMapper().writeValueAsString(result));
        receipt.setService("AccountAdministrationService");
        receipt.setOperation(operation);
        receipt.setIdempotencyKey(command.idempotency().idempotencyKey());
        return receipt;
    }
}
