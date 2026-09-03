package com.ulticode.modules.admin.service.impl;

import com.ulticode.auth.api.command.ChangeAuthorizationCommand;
import com.ulticode.auth.api.dto.AuthorizationSnapshotDTO;
import com.ulticode.auth.api.service.AccountAdministrationService;
import com.ulticode.common.annotation.Audited;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.time.TimeSource;
import com.ulticode.common.time.TimeSourceHolder;
import com.ulticode.common.util.AuditContext;
import com.ulticode.common.util.TraceIdUtil;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.modules.admin.query.AdminUserDetailQuery;
import com.ulticode.modules.admin.query.AdminUserDetailResult;
import com.ulticode.modules.admin.service.UserPermissionService;
import com.ulticode.common.rpc.RpcResult;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("UserPermissionServiceImpl")
class UserPermissionServiceImplTest {

    @Mock
    private AccountAdministrationService accountAdministrationService;
    @Mock
    private AdminUserDetailQuery adminUserDetailQuery;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private Clock clock;
    private UserPermissionServiceImpl userPermissionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.systemDefault());
        userPermissionService = new UserPermissionServiceImpl(
                adminUserDetailQuery, clock, currentUserProvider);
        ReflectionTestUtils.setField(userPermissionService, "accountAdministrationService",
                accountAdministrationService);
        lenient().when(currentUserProvider.getCurrentUserId()).thenReturn("admin-001");
        lenient().when(adminUserDetailQuery.loadUserDetail("user-123"))
                .thenReturn(detailResult(Set.of()));
    }

    @AfterEach
    void tearDown() {
        TimeSourceHolder.reset();
        AuditContext.clear();
    }


    private AdminUserDetailResult detailResult(Set<String> permissions) {
        AdminUserVO user = new AdminUserVO();
        user.setId("user-123");
        user.setRole("USER");
        AdminUserDetailResult.PermissionSnapshot snapshot =
                new AdminUserDetailResult.PermissionSnapshot(
                        "auth.authorization-snapshot", "USER", permissions, 7L);
        return AdminUserDetailResult.found(
                user,
                AdminUserDetailResult.Section.ok(),
                AdminUserDetailResult.Section.ok(),
                AdminUserDetailResult.Section.ok(),
                snapshot);
    }

    private AdminUserDetailResult unavailablePermissions(String reason) {
        AdminUserVO user = new AdminUserVO();
        user.setId("user-123");
        user.setRole("USER");
        return AdminUserDetailResult.found(
                user,
                AdminUserDetailResult.Section.ok(),
                AdminUserDetailResult.Section.ok(),
                AdminUserDetailResult.Section.unavailable(reason),
                null);
    }

    @Test
    @DisplayName("assignUserPermission succeeds for non-super-admin permission")
    void assignPermissionSuccess() {
        when(accountAdministrationService.changeAuthorization(any(ChangeAuthorizationCommand.class))).thenReturn(RpcResult.success(new com.ulticode.auth.api.dto.AuthorizationSnapshotDTO("user-123", "USER", java.util.Collections.emptySet(), 0L), "t-test"));

        AdminUserVO result = userPermissionService.assignUserPermission("user-123", "READ", "PROBLEM", null);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("user-123");
        verify(accountAdministrationService).changeAuthorization(any(ChangeAuthorizationCommand.class));
    }

    @Test
    @DisplayName("permission RPC failure is propagated instead of reported as success")
    void permissionMutationFailureIsPropagated() {
        when(accountAdministrationService.changeAuthorization(any(ChangeAuthorizationCommand.class)))
                .thenReturn(RpcResult.failure(com.ulticode.auth.api.error.AuthErrorCode.AUTHORIZATION_VERSION_CONFLICT,
                        "t-test"));

        assertThatThrownBy(() -> userPermissionService.assignUserPermission(
                "user-123", "READ", "PROBLEM", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Permission change failed on Auth provider");
    }

    @Test
    @DisplayName("missing Auth permission provider fails closed")
    void missingPermissionProviderFailsClosed() {
        ReflectionTestUtils.setField(userPermissionService, "accountAdministrationService", null);

        assertThatThrownBy(() -> userPermissionService.assignUserPermission(
                "user-123", "READ", "PROBLEM", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("AccountAdministrationService unavailable");
    }

    @Test
    @DisplayName("revokeUserPermission returns success for an already absent permission")
    void revokePermissionSuccess() {
        // P3-ADMIN-003: a proven absent revoke is an idempotent no-op.
        AdminUserVO result = userPermissionService.revokeUserPermission(
                "user-123", "READ", "PROBLEM");

        assertThat(result).isNotNull();
        verify(accountAdministrationService, never())
                .changeAuthorization(any(ChangeAuthorizationCommand.class));
    }

    @Test
    @DisplayName("assignUserPermission preserves existing permissions in target full set")
    void assignPermissionPreservesExistingPermissions() {
        when(currentUserProvider.hasRole("SUPER_ADMIN")).thenReturn(true);
        when(adminUserDetailQuery.loadUserDetail("user-123"))
                .thenReturn(detailResult(Set.of("READ:PROBLEM")));
        when(accountAdministrationService.changeAuthorization(any(ChangeAuthorizationCommand.class)))
                .thenReturn(RpcResult.success(
                        new AuthorizationSnapshotDTO(
                                "user-123", "USER",
                                Set.of("READ:PROBLEM", "WRITE:PROBLEM"), 8L),
                        "t-test"));

        userPermissionService.assignUserPermission(
                "user-123", "WRITE", "PROBLEM", null);

        ArgumentCaptor<ChangeAuthorizationCommand> captor =
                ArgumentCaptor.forClass(ChangeAuthorizationCommand.class);
        verify(accountAdministrationService).changeAuthorization(captor.capture());

        ChangeAuthorizationCommand cmd = captor.getValue();
        assertThat(cmd.permissions()).containsExactlyInAnyOrder(
                "READ:PROBLEM", "WRITE:PROBLEM");
        assertThat(cmd.expectedVersion()).isEqualTo(7L);
        assertThat(cmd.actor().actorType()).isEqualTo("SUPER_ADMIN");
    }

    @Test
    @DisplayName("revokeUserPermission removes specified permission while preserving remaining permissions")
    void revokePermissionPreservesRemainingPermissions() {
        when(adminUserDetailQuery.loadUserDetail("user-123"))
                .thenReturn(detailResult(Set.of("READ:PROBLEM", "WRITE:PROBLEM")));
        when(accountAdministrationService.changeAuthorization(any(ChangeAuthorizationCommand.class)))
                .thenReturn(RpcResult.success(
                        new AuthorizationSnapshotDTO(
                                "user-123", "USER", Set.of("WRITE:PROBLEM"), 8L),
                        "t-test"));

        userPermissionService.revokeUserPermission(
                "user-123", "READ", "PROBLEM");

        ArgumentCaptor<ChangeAuthorizationCommand> captor =
                ArgumentCaptor.forClass(ChangeAuthorizationCommand.class);
        verify(accountAdministrationService).changeAuthorization(captor.capture());

        ChangeAuthorizationCommand cmd = captor.getValue();
        assertThat(cmd.permissions()).containsExactly("WRITE:PROBLEM");
        assertThat(cmd.expectedVersion()).isEqualTo(7L);
    }

    @Test
    @DisplayName("Request-scoped idempotency key is stable across retries in same trace context")
    void idempotencyKeyIsStableInSameTraceContext() {
        when(accountAdministrationService.changeAuthorization(any(ChangeAuthorizationCommand.class))).thenReturn(RpcResult.success(new com.ulticode.auth.api.dto.AuthorizationSnapshotDTO("user-123", "USER", java.util.Collections.emptySet(), 0L), "t-test"));

        TimeSourceHolder.install(new TimeSource() {
            @Override
            public long wallMillis() {
                return 1700000000000L;
            }

            @Override
            public long monotonicNanos() {
                return 1000000L;
            }
        });

        userPermissionService.assignUserPermission("user-123", "READ", "PROBLEM", null);
        userPermissionService.assignUserPermission("user-123", "READ", "PROBLEM", null);

        ArgumentCaptor<ChangeAuthorizationCommand> captor = ArgumentCaptor.forClass(ChangeAuthorizationCommand.class);
        verify(accountAdministrationService, times(2)).changeAuthorization(captor.capture());

        List<ChangeAuthorizationCommand> cmds = captor.getAllValues();
        assertThat(cmds.get(0).idempotency().idempotencyKey()).isEqualTo(cmds.get(1).idempotency().idempotencyKey());
        assertThat(cmds.get(0).idempotency().idempotencyKey()).contains("t-1700000000000");
    }

    @Test
    @DisplayName("MANAGE_PERMISSIONS:SYSTEM grant by non-SUPER_ADMIN throws FORBIDDEN")
    void managePermissionsSystemByAdminThrows() {
        when(currentUserProvider.hasRole("SUPER_ADMIN")).thenReturn(false);

        assertThatThrownBy(() -> userPermissionService.assignUserPermission(
                "user-123", "MANAGE_PERMISSIONS", "SYSTEM", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(AdminErrorCode.FORBIDDEN);
                });
    }

    @Test
    @DisplayName("MANAGE_PERMISSIONS:SYSTEM grant by SUPER_ADMIN is allowed")
    void managePermissionsSystemBySuperAdminAllowed() {
        when(currentUserProvider.hasRole("SUPER_ADMIN")).thenReturn(true);
        when(accountAdministrationService.changeAuthorization(any(ChangeAuthorizationCommand.class))).thenReturn(RpcResult.success(new com.ulticode.auth.api.dto.AuthorizationSnapshotDTO("user-123", "USER", java.util.Collections.emptySet(), 0L), "t-test"));

        AdminUserVO result = userPermissionService.assignUserPermission(
                "user-123", "MANAGE_PERMISSIONS", "SYSTEM", null);

        assertThat(result).isNotNull();
        verify(accountAdministrationService).changeAuthorization(any(ChangeAuthorizationCommand.class));
    }

    @Test
    @DisplayName("guard is case-insensitive: lowercase manage_permissions:system still blocked for non-SUPER_ADMIN")
    void managePermissionsSystemCaseInsensitive() {
        when(currentUserProvider.hasRole("SUPER_ADMIN")).thenReturn(false);

        assertThatThrownBy(() -> userPermissionService.assignUserPermission(
                "user-123", "manage_permissions", "system", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(AdminErrorCode.FORBIDDEN);
                });
    }
    @Test
    @DisplayName("proven empty authorization snapshot remains writable")
    void provenEmptyPermissionsRemainWritable() {
        when(accountAdministrationService.changeAuthorization(any(ChangeAuthorizationCommand.class)))
                .thenReturn(RpcResult.success(
                        new AuthorizationSnapshotDTO(
                                "user-123", "USER", Set.of(), 7L),
                        "t-write"));

        AdminUserVO result = userPermissionService.assignUserPermission(
                "user-123", "READ", "PROBLEM", null);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("user-123");
        verify(accountAdministrationService).changeAuthorization(any(ChangeAuthorizationCommand.class));
    }

    @Test
    @DisplayName("null authorization provider never dispatches a replacement write")
    void nullAuthorizationProviderDoesNotWrite() {
        when(adminUserDetailQuery.loadUserDetail("user-123"))
                .thenReturn(unavailablePermissions(
                        "Authorization snapshot provider unavailable"));

        assertThatThrownBy(() -> userPermissionService.assignUserPermission(
                "user-123", "READ", "PROBLEM", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Authorization snapshot");

        verify(accountAdministrationService, never())
                .changeAuthorization(any(ChangeAuthorizationCommand.class));
    }

    @Test
    @DisplayName("authorization provider transport failure never dispatches a replacement write")
    void authorizationProviderFailureDoesNotWrite() {
        when(adminUserDetailQuery.loadUserDetail("user-123"))
                .thenThrow(new RuntimeException("transport unavailable"));

        assertThatThrownBy(() -> userPermissionService.assignUserPermission(
                "user-123", "READ", "PROBLEM", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Authorization snapshot");

        verify(accountAdministrationService, never())
                .changeAuthorization(any(ChangeAuthorizationCommand.class));
    }

    @Test
    @DisplayName("unsuccessful authorization provider never dispatches a replacement write")
    void unsuccessfulAuthorizationProviderDoesNotWrite() {
        when(adminUserDetailQuery.loadUserDetail("user-123"))
                .thenReturn(unavailablePermissions(
                        "Authorization snapshot returned failure"));

        assertThatThrownBy(() -> userPermissionService.assignUserPermission(
                "user-123", "READ", "PROBLEM", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Authorization snapshot");

        verify(accountAdministrationService, never())
                .changeAuthorization(any(ChangeAuthorizationCommand.class));
    }
    @Test
    @DisplayName("permission snapshot rejection records a safe audit reason")
    void permissionSnapshotRejectionRecordsAuditReason() {
        when(adminUserDetailQuery.loadUserDetail("user-123"))
                .thenReturn(unavailablePermissions(
                        "Authorization snapshot provider unavailable"));

        assertThatThrownBy(() -> userPermissionService.assignUserPermission(
                "user-123", "READ", "PROBLEM", null))
                .isInstanceOf(BusinessException.class);

        assertThat(AuditContext.getOldValues())
                .containsEntry("permissionSnapshotStatus", "UNAVAILABLE")
                .containsEntry("permissionSnapshotReason",
                        "UNAVAILABLE: Authorization snapshot provider unavailable");
        verify(accountAdministrationService, never())
                .changeAuthorization(any(ChangeAuthorizationCommand.class));
    }
}
