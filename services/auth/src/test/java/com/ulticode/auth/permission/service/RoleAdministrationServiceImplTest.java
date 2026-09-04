package com.ulticode.auth.permission.service;

import com.ulticode.auth.account.AuthAccountPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.api.command.ChangeRoleCommand;
import com.ulticode.auth.api.command.PermissionMutationCommand;
import com.ulticode.auth.api.dto.AccountMutationDTO;
import com.ulticode.auth.api.dto.AuthorizationMutationDTO;
import com.ulticode.auth.authorization.AuthorizationMutationWorkflow;
import com.ulticode.auth.authorization.RoleMutationWorkflow;
import com.ulticode.auth.idempotency.CommandReceiptExecutor;
import com.ulticode.auth.permission.entity.UserPermission;
import com.ulticode.auth.permission.service.impl.RoleAdministrationServiceImpl;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.rpc.RpcResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleAdministrationServiceImplTest {

    @Mock
    private AuthAccountPort authAccountPort;

    @Mock
    private PermissionService permissionService;

    @Mock
    private AuthorizationMutationWorkflow authorizationMutationWorkflow;

    @Mock
    private RoleMutationWorkflow roleMutationWorkflow;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private RoleAdministrationServiceImpl service;

    @BeforeEach
    void setUp() {
        CommandReceiptExecutor receiptExecutor = new CommandReceiptExecutor(
                null, new com.fasterxml.jackson.databind.ObjectMapper(),
                java.time.Clock.systemUTC());
        service = new RoleAdministrationServiceImpl(
                authAccountPort, permissionService, authorizationMutationWorkflow,
                roleMutationWorkflow, receiptExecutor, currentUserProvider);
    }

    @Test
    void roleChangeDelegatesToRoleMutationWorkflowWithExpectedVersion() {
        when(authAccountPort.findById("user-1"))
                .thenReturn(Optional.of(account(5L)));
        when(roleMutationWorkflow.changeRole(any()))
                .thenReturn(RpcResult.success(
                        new AccountMutationDTO("user-1", "alice", "alice@example.com",
                                "ADMIN", true, false, 6L, false),
                        "trace-1"));

        assertThat(service.changeRole("user-1", "ADMIN", "admin-1")).isEqualTo("ADMIN");

        ArgumentCaptor<ChangeRoleCommand> captor = ArgumentCaptor.forClass(ChangeRoleCommand.class);
        verify(roleMutationWorkflow).changeRole(captor.capture());
        assertThat(captor.getValue().accountId()).isEqualTo("user-1");
        assertThat(captor.getValue().expectedVersion()).isEqualTo(5L);
        assertThat(captor.getValue().actor().actorId()).isEqualTo("admin-1");
    }

    @Test
    void permissionGrantDelegatesToAuthDeltaAndReadsOnlyForLegacyResponse() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 12, 31, 23, 59);
        UserPermission permission = permission("grant-1", expiresAt);
        when(authAccountPort.findById("user-1"))
                .thenReturn(Optional.of(account(7L)));
        when(authorizationMutationWorkflow.mutatePermission(any()))
                .thenReturn(RpcResult.success(
                        new AuthorizationMutationDTO("user-1", "GRANT", "READ", "PROBLEM",
                                "direct", expiresAt.atOffset(ZoneOffset.UTC), 8L, true),
                        "trace-1"));
        when(permissionService.getUserPermissions("user-1"))
                .thenReturn(List.of(permission));

        RoleAdministrationService.PermissionGrant result = service.grantPermission(
                "user-1", " read ", " problem ", expiresAt, "admin-1");

        assertThat(result.id()).isEqualTo("grant-1");
        ArgumentCaptor<PermissionMutationCommand> captor =
                ArgumentCaptor.forClass(PermissionMutationCommand.class);
        verify(authorizationMutationWorkflow).mutatePermission(captor.capture());
        assertThat(captor.getValue().operation()).isEqualTo(PermissionMutationCommand.Operation.GRANT);
        assertThat(captor.getValue().action()).isEqualTo("READ");
        assertThat(captor.getValue().resource()).isEqualTo("PROBLEM");
        assertThat(captor.getValue().expiresAt())
                .isEqualTo(OffsetDateTime.of(2026, 12, 31, 23, 59, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void permissionRevokeReturnsMutationAcknowledgement() {
        when(authAccountPort.findById("user-1"))
                .thenReturn(Optional.of(account(8L)));
        when(authorizationMutationWorkflow.mutatePermission(any()))
                .thenReturn(RpcResult.success(
                        new AuthorizationMutationDTO("user-1", "REVOKE", "READ", "PROBLEM",
                                "direct", null, 9L, true),
                        "trace-1"));

        assertThat(service.revokePermission("user-1", "READ", "PROBLEM", "admin-1"))
                .isTrue();
        ArgumentCaptor<PermissionMutationCommand> captor =
                ArgumentCaptor.forClass(PermissionMutationCommand.class);
        verify(authorizationMutationWorkflow).mutatePermission(captor.capture());
        assertThat(captor.getValue().operation()).isEqualTo(PermissionMutationCommand.Operation.REVOKE);
    }

    private static UserPermission permission(String id, LocalDateTime expiresAt) {
        UserPermission permission = new UserPermission();
        permission.setId(id);
        permission.setUserId("user-1");
        permission.setAction("READ");
        permission.setResource("PROBLEM");
        permission.setGrantedBy("admin-1");
        permission.setGrantedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        permission.setExpiresAt(expiresAt);
        return permission;
    }

    private static AuthAccountRecord account(long version) {
        return new AuthAccountRecord(
                "user-1", "alice", "alice@example.com", "secret", "USER",
                true, false, null, LocalDateTime.of(2025, 1, 1, 0, 0), version);
    }
}
