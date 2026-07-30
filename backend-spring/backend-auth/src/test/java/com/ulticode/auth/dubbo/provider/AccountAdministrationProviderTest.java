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
import com.ulticode.auth.idempotency.entity.AuthCommandReceiptEntity;
import com.ulticode.auth.idempotency.mapper.AuthCommandReceiptMapper;
import com.ulticode.auth.permission.service.PermissionService;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    private ObjectMapper objectMapper;
    private AccountAdministrationEngine engine;
    private AccountAdministrationProvider provider;

    private ActorDelegation actor;
    private TraceMetadata trace;

    @BeforeEach
    void setUp() {
        authAccountPort = mock(AuthAccountPort.class);
        permissionService = mock(PermissionService.class);
        receiptMapper = mock(AuthCommandReceiptMapper.class);
        objectMapper = new ObjectMapper();
        engine = new AccountAdministrationEngine(authAccountPort, permissionService);
        provider = new AccountAdministrationProvider(engine, receiptMapper, objectMapper);

        actor = new ActorDelegation("ADMIN", "admin-1", "org-1", "reason");
        trace = new TraceMetadata("t-123", "span-1", null, null);
    }

    @Test
    @DisplayName("changeState succeeds and increments version upon atomic CAS match")
    void changeStateSuccess() {
        AuthAccountRecord record = new AuthAccountRecord("user-1", "alice", "alice@example.com", "secret", "USER", true, false, null, null, 2L);
        when(authAccountPort.findById("user-1")).thenReturn(Optional.of(record));
        when(authAccountPort.updateAccountIfVersion("user-1", false, false, "USER", 2L)).thenReturn(true);

        ChangeAccountStateCommand command = new ChangeAccountStateCommand(
                "cmd-1", IdMetadata.mint(), actor, trace, "user-1", 2L,
                ChangeAccountStateCommand.AccountStateAction.DISABLE, "disable user"
        );

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

        ChangeAccountStateCommand command = new ChangeAccountStateCommand(
                "cmd-1", IdMetadata.mint(), actor, trace, "user-99", 1L,
                ChangeAccountStateCommand.AccountStateAction.DISABLE, "test"
        );

        RpcResult<AccountStateDTO> result = provider.changeState(command);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AuthErrorCode.ACCOUNT_NOT_FOUND.code());
    }

    @Test
    @DisplayName("changeState returns AUTHORIZATION_VERSION_CONFLICT when expected version is stale")
    void changeStateVersionConflict() {
        AuthAccountRecord record = new AuthAccountRecord("user-1", "alice", "alice@example.com", "secret", "USER", true, false, null, null, 5L);
        when(authAccountPort.findById("user-1")).thenReturn(Optional.of(record));

        ChangeAccountStateCommand command = new ChangeAccountStateCommand(
                "cmd-1", IdMetadata.mint(), actor, trace, "user-1", 2L,
                ChangeAccountStateCommand.AccountStateAction.DISABLE, "stale"
        );

        RpcResult<AccountStateDTO> result = provider.changeState(command);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AuthErrorCode.AUTHORIZATION_VERSION_CONFLICT.code());
    }

    @Test
    @DisplayName("changeState replays DTO result from receipt on duplicate idempotency key")
    void changeStateIdempotencyReplay() throws Exception {
        AccountStateDTO cachedDto = new AccountStateDTO("user-1", false, false, 3L);
        AuthCommandReceiptEntity receipt = new AuthCommandReceiptEntity();
        receipt.setStatus("SUCCESS");
        receipt.setResultPayload(objectMapper.writeValueAsString(cachedDto));

        when(receiptMapper.findByReceiptKey("AccountAdministrationService", "changeState", "key-abc"))
                .thenReturn(receipt);

        IdMetadata idempotency = IdMetadata.of("key-abc", null);
        ChangeAccountStateCommand command = new ChangeAccountStateCommand(
                "cmd-1", idempotency, actor, trace, "user-1", 2L,
                ChangeAccountStateCommand.AccountStateAction.DISABLE, "replay"
        );

        RpcResult<AccountStateDTO> result = provider.changeState(command);

        assertThat(result.success()).isTrue();
        assertThat(result.data().version()).isEqualTo(3L);
        verify(authAccountPort, never()).updateAccountIfVersion(anyString(), anyBoolean(), anyBoolean(), anyString(), anyLong());
    }

    @Test
    @DisplayName("changeAuthorization updates role, synchronizes permissions and bumps version")
    void changeAuthorizationSuccess() {
        AuthAccountRecord record = new AuthAccountRecord("user-1", "alice", "alice@example.com", "secret", "USER", true, false, null, null, 1L);
        when(authAccountPort.findById("user-1")).thenReturn(Optional.of(record));
        when(authAccountPort.updateAccountIfVersion("user-1", true, false, "ADMIN", 1L)).thenReturn(true);
        when(permissionService.getUserPermissionStrings("user-1")).thenReturn(List.of("READ:PROBLEM", "WRITE:PROBLEM"));

        ChangeAuthorizationCommand command = new ChangeAuthorizationCommand(
                "cmd-2", IdMetadata.mint(), actor, trace, "user-1", 1L,
                "ADMIN", Set.of("READ:PROBLEM", "WRITE:PROBLEM"), "promote to admin"
        );

        RpcResult<AuthorizationSnapshotDTO> result = provider.changeAuthorization(command);

        assertThat(result.success()).isTrue();
        assertThat(result.data().role()).isEqualTo("ADMIN");
        assertThat(result.data().version()).isEqualTo(2L);
        assertThat(result.data().permissions()).containsExactlyInAnyOrder("READ:PROBLEM", "WRITE:PROBLEM");
        verify(receiptMapper).insert(any(AuthCommandReceiptEntity.class));
    }
}
