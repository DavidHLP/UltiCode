package com.ulticode.auth.authorization;

import com.ulticode.auth.account.AuthAccountPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.api.command.ActorDelegation;
import com.ulticode.auth.api.command.PermissionMutationCommand;
import com.ulticode.auth.api.dto.AuthorizationMutationDTO;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.permission.PermissionVocabulary;
import com.ulticode.auth.permission.entity.UserPermission;
import com.ulticode.auth.permission.service.PermissionService;
import com.ulticode.common.audit.AuditSinkPort;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyLong;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultAuthorizationMutationWorkflowTest {

    @Mock
    private AuthAccountPort authAccountPort;

    @Mock
    private PermissionService permissionService;

    @Mock
    private AuditSinkPort auditSinkPort;

    private DefaultAuthorizationMutationWorkflow workflow;

    @BeforeEach
    void setUp() {
        workflow = new DefaultAuthorizationMutationWorkflow(
                authAccountPort, permissionService, auditSinkPort, new PermissionVocabulary());
    }

    @Test
    void grantPreservesExpiryAndAuthenticatedActor() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 12, 31, 23, 59);
        OffsetDateTime expiresAtWire = expiresAt.atOffset(ZoneOffset.UTC);
        UserPermission granted = permission("grant-1", expiresAt);
        when(authAccountPort.findById("user-1"))
                .thenReturn(Optional.of(account("user-1", "ADMIN", 4L)));
        when(permissionService.getUserPermissions("user-1")).thenReturn(List.of());
        when(permissionService.assignPermission(
                "user-1", "READ", "PROBLEM", expiresAt, "admin-1"))
                .thenReturn(granted);
        when(authAccountPort.bumpAuthzVersionIfExpected("user-1", 4L)).thenReturn(true);

        RpcResult<AuthorizationMutationDTO> result = workflow.mutatePermission(
                command(PermissionMutationCommand.Operation.GRANT, 4L, expiresAtWire));

        assertThat(result.success()).isTrue();
        assertThat(result.data().changed()).isTrue();
        assertThat(result.data().expiresAt()).isEqualTo(expiresAtWire);
        assertThat(result.data().version()).isEqualTo(5L);
        verify(permissionService).assignPermission(
                "user-1", "READ", "PROBLEM", expiresAt, "admin-1");
        verify(authAccountPort).bumpAuthzVersionIfExpected("user-1", 4L);
        verify(auditSinkPort).log(
                eq("admin-1"), eq("user-1"), eq("AUTHORIZATION_CHANGED"),
                eq("USER_AUTHORIZATION"), eq("user-1"), any(), any(),
                eq("unknown"), eq(null));
    }

    @Test
    void revokeOnlyDeletesDirectPermissionNotRolePermission() {
        UserPermission direct = permission("grant-1", null);
        when(authAccountPort.findById("user-1"))
                .thenReturn(Optional.of(account("user-1", "ADMIN", 4L)));
        when(permissionService.getUserPermissions("user-1")).thenReturn(List.of(direct));
        when(permissionService.revokePermission("user-1", "READ", "PROBLEM"))
                .thenReturn(true);
        when(authAccountPort.bumpAuthzVersionIfExpected("user-1", 4L)).thenReturn(true);

        RpcResult<AuthorizationMutationDTO> result = workflow.mutatePermission(
                command(PermissionMutationCommand.Operation.REVOKE, 4L, null));

        assertThat(result.success()).isTrue();
        assertThat(result.data().changed()).isTrue();
        assertThat(result.data().source()).isEqualTo("direct");
        verify(permissionService).revokePermission("user-1", "READ", "PROBLEM");
    }

    @Test
    void revokeRoleInheritedPermissionIsNoOp() {
        when(authAccountPort.findById("user-1"))
                .thenReturn(Optional.of(account("user-1", "ADMIN", 4L)));
        when(permissionService.getUserPermissions("user-1")).thenReturn(List.of());
        when(permissionService.revokePermission("user-1", "READ", "PROBLEM"))
                .thenReturn(false);

        RpcResult<AuthorizationMutationDTO> result = workflow.mutatePermission(
                command(PermissionMutationCommand.Operation.REVOKE, 4L, null));

        assertThat(result.success()).isTrue();
        assertThat(result.data().changed()).isFalse();
        assertThat(result.data().version()).isEqualTo(4L);
        verify(permissionService).revokePermission("user-1", "READ", "PROBLEM");
        verify(authAccountPort, never()).bumpAuthzVersionIfExpected(any(), anyLong());
    }

    @Test
    void staleExpectedVersionFailsClosedBeforeMutation() {
        when(authAccountPort.findById("user-1"))
                .thenReturn(Optional.of(account("user-1", "ADMIN", 5L)));

        RpcResult<AuthorizationMutationDTO> result = workflow.mutatePermission(
                command(PermissionMutationCommand.Operation.GRANT, 4L, null));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(
                AuthErrorCode.AUTHORIZATION_VERSION_CONFLICT.code());
        verify(permissionService, never()).getUserPermissions(any());
        verify(auditSinkPort, never()).log(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void casFailureAfterDirectMutationThrowsForTransactionRollback() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 12, 31, 23, 59);
        when(authAccountPort.findById("user-1"))
                .thenReturn(Optional.of(account("user-1", "ADMIN", 4L)));
        when(permissionService.getUserPermissions("user-1")).thenReturn(List.of());
        when(permissionService.assignPermission(
                "user-1", "READ", "PROBLEM", expiresAt, "admin-1"))
                .thenReturn(permission("grant-1", expiresAt));
        when(authAccountPort.bumpAuthzVersionIfExpected("user-1", 4L)).thenReturn(false);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> workflow.mutatePermission(
                        command(PermissionMutationCommand.Operation.GRANT, 4L,
                                expiresAt.atOffset(ZoneOffset.UTC))))
                .isInstanceOf(AuthBusinessException.class)
                .hasMessageContaining("Authorization version conflict");
        verify(auditSinkPort, never()).log(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void missingAccountFailsClosed() {
        when(authAccountPort.findById("user-1")).thenReturn(Optional.empty());

        RpcResult<AuthorizationMutationDTO> result = workflow.mutatePermission(
                command(PermissionMutationCommand.Operation.GRANT, 4L, null));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AuthErrorCode.ACCOUNT_NOT_FOUND.code());
    }

    @Test
    void permissionValidationRejectsExpiryOnRevoke() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new PermissionMutationCommand(
                        "cmd-1", IdMetadata.mint(),
                        new ActorDelegation("ADMIN", "admin-1", "admin-1", "test"),
                        new TraceMetadata("trace-1", null, null, null),
                        "user-1", PermissionMutationCommand.Operation.REVOKE,
                        "READ", "PROBLEM", OffsetDateTime.now(ZoneOffset.UTC), 4L, "test"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonSuperAdminCannotGrantSystemPermission() {
        when(authAccountPort.findById("user-1"))
                .thenReturn(Optional.of(account("user-1", "ADMIN", 4L)));

        RpcResult<AuthorizationMutationDTO> result = workflow.mutatePermission(
                new PermissionMutationCommand(
                        "cmd-system", IdMetadata.mint(),
                        new ActorDelegation("ADMIN", "admin-1", "admin-1", "test"),
                        new TraceMetadata("trace-system", null, null, null),
                        "user-1", PermissionMutationCommand.Operation.GRANT,
                        " manage_permissions ", " system ", null, 4L, "test"));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code())
                .isEqualTo(com.ulticode.common.error.BaseErrorCode.FORBIDDEN.code());
        verify(permissionService, never()).getUserPermissions(any());
    }

    private PermissionMutationCommand command(
            PermissionMutationCommand.Operation operation,
            long expectedVersion,
            OffsetDateTime expiresAt) {
        return new PermissionMutationCommand(
                "cmd-1", IdMetadata.of("key-1", null),
                new ActorDelegation("ADMIN", "admin-1", "admin-1", "test"),
                new TraceMetadata("trace-1", null, null, null),
                "user-1", operation, "READ", "PROBLEM", expiresAt,
                expectedVersion, "test");
    }

    private static UserPermission permission(String id, LocalDateTime expiresAt) {
        UserPermission permission = new UserPermission();
        permission.setId(id);
        permission.setUserId("user-1");
        permission.setAction("READ");
        permission.setResource("PROBLEM");
        permission.setGrantedBy("admin-1");
        permission.setExpiresAt(expiresAt);
        return permission;
    }

    private static AuthAccountRecord account(String id, String role, long version) {
        return new AuthAccountRecord(
                id, "alice", "alice@example.com", "secret", role,
                true, false, null, null, version);
    }
}
