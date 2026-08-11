package com.ulticode.modules.admin.service.impl;

import com.ulticode.auth.api.command.ChangeAuthorizationCommand;
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
import com.ulticode.modules.admin.projection.AdminUserProjection;
import com.ulticode.modules.admin.service.UserPermissionService;
import com.ulticode.modules.admin.projection.AdminUserSummary;
import com.ulticode.modules.admin.projection.AdminUserEnricher;
import com.ulticode.auth.api.service.AccountAdministrationService;
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
    private AdminUserEnricher userEnricher;
    @Mock
    private AccountAdministrationService accountAdministrationService;
    @Mock
    private AdminUserProjection adminUserProjection;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private Clock clock;
    private UserPermissionServiceImpl userPermissionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.systemDefault());
        userPermissionService = new UserPermissionServiceImpl(
                userEnricher,
                adminUserProjection, clock, currentUserProvider);
        ReflectionTestUtils.setField(userPermissionService, "accountAdministrationService", accountAdministrationService);
        lenient().when(currentUserProvider.getCurrentUserId()).thenReturn("admin-001");
    }

    @AfterEach
    void tearDown() {
        TimeSourceHolder.reset();
    }

    private AdminUserSummary createValidUser() {
        return new AdminUserSummary("user-123", "testuser", "USER", null, null, "test@example.com");
    }

    @Test
    @DisplayName("assignUserPermission succeeds for non-super-admin permission")
    void assignPermissionSuccess() {
        AdminUserSummary u = createValidUser();
        when(userEnricher.enrichOne("user-123")).thenReturn(u);
        AdminUserVO vo = new AdminUserVO();
        vo.setId("user-123");
        when(adminUserProjection.getUserById("user-123")).thenReturn(vo);
        when(accountAdministrationService.changeAuthorization(any(ChangeAuthorizationCommand.class))).thenReturn(RpcResult.success(new com.ulticode.auth.api.dto.AuthorizationSnapshotDTO("user-123", "USER", java.util.Collections.emptySet(), 0L), "t-test"));

        AdminUserVO result = userPermissionService.assignUserPermission("user-123", "READ", "PROBLEM", null);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("user-123");
        verify(accountAdministrationService).changeAuthorization(any(ChangeAuthorizationCommand.class));
    }

    @Test
    @DisplayName("revokeUserPermission succeeds")
    void revokePermissionSuccess() {
        AdminUserSummary u = createValidUser();
        when(userEnricher.enrichOne("user-123")).thenReturn(u);
        AdminUserVO vo = new AdminUserVO();
        vo.setId("user-123");
        when(adminUserProjection.getUserById("user-123")).thenReturn(vo);
        when(accountAdministrationService.changeAuthorization(any(ChangeAuthorizationCommand.class))).thenReturn(RpcResult.success(new com.ulticode.auth.api.dto.AuthorizationSnapshotDTO("user-123", "USER", java.util.Collections.emptySet(), 0L), "t-test"));

        AdminUserVO result = userPermissionService.revokeUserPermission("user-123", "READ", "PROBLEM");

        assertThat(result).isNotNull();
        verify(accountAdministrationService).changeAuthorization(any(ChangeAuthorizationCommand.class));
    }

    @Test
    @DisplayName("assignUserPermission preserves existing permissions in target full set")
    void assignPermissionPreservesExistingPermissions() {
        AdminUserSummary u = createValidUser();
        when(userEnricher.enrichOne("user-123")).thenReturn(u);

        AdminUserVO.PermissionInfo existingPerm = new AdminUserVO.PermissionInfo();
        existingPerm.setAction("READ");
        existingPerm.setResource("PROBLEM");

        AdminUserVO vo = new AdminUserVO();
        vo.setId("user-123");
        vo.setPermissions(List.of(existingPerm));
        when(adminUserProjection.getUserById("user-123")).thenReturn(vo);
        when(accountAdministrationService.changeAuthorization(any(ChangeAuthorizationCommand.class))).thenReturn(RpcResult.success(new com.ulticode.auth.api.dto.AuthorizationSnapshotDTO("user-123", "USER", java.util.Collections.emptySet(), 0L), "t-test"));

        userPermissionService.assignUserPermission("user-123", "WRITE", "PROBLEM", null);

        ArgumentCaptor<ChangeAuthorizationCommand> captor = ArgumentCaptor.forClass(ChangeAuthorizationCommand.class);
        verify(accountAdministrationService).changeAuthorization(captor.capture());

        ChangeAuthorizationCommand cmd = captor.getValue();
        assertThat(cmd.permissions()).containsExactlyInAnyOrder("READ:PROBLEM", "WRITE:PROBLEM");
    }

    @Test
    @DisplayName("revokeUserPermission removes specified permission while preserving remaining permissions")
    void revokePermissionPreservesRemainingPermissions() {
        AdminUserSummary u = createValidUser();
        when(userEnricher.enrichOne("user-123")).thenReturn(u);

        AdminUserVO.PermissionInfo perm1 = new AdminUserVO.PermissionInfo();
        perm1.setAction("READ");
        perm1.setResource("PROBLEM");

        AdminUserVO.PermissionInfo perm2 = new AdminUserVO.PermissionInfo();
        perm2.setAction("WRITE");
        perm2.setResource("PROBLEM");

        AdminUserVO vo = new AdminUserVO();
        vo.setId("user-123");
        vo.setPermissions(List.of(perm1, perm2));
        when(adminUserProjection.getUserById("user-123")).thenReturn(vo);
        when(accountAdministrationService.changeAuthorization(any(ChangeAuthorizationCommand.class))).thenReturn(RpcResult.success(new com.ulticode.auth.api.dto.AuthorizationSnapshotDTO("user-123", "USER", java.util.Collections.emptySet(), 0L), "t-test"));

        userPermissionService.revokeUserPermission("user-123", "READ", "PROBLEM");

        ArgumentCaptor<ChangeAuthorizationCommand> captor = ArgumentCaptor.forClass(ChangeAuthorizationCommand.class);
        verify(accountAdministrationService).changeAuthorization(captor.capture());

        ChangeAuthorizationCommand cmd = captor.getValue();
        assertThat(cmd.permissions()).containsExactly("WRITE:PROBLEM");
    }

    @Test
    @DisplayName("Request-scoped idempotency key is stable across retries in same trace context")
    void idempotencyKeyIsStableInSameTraceContext() {
        AdminUserSummary u = createValidUser();
        when(userEnricher.enrichOne("user-123")).thenReturn(u);
        AdminUserVO vo = new AdminUserVO();
        vo.setId("user-123");
        when(adminUserProjection.getUserById("user-123")).thenReturn(vo);
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
        AdminUserSummary u = createValidUser();
        when(userEnricher.enrichOne("user-123")).thenReturn(u);
        when(currentUserProvider.hasRole("SUPER_ADMIN")).thenReturn(true);
        AdminUserVO vo = new AdminUserVO();
        vo.setId("user-123");
        when(adminUserProjection.getUserById("user-123")).thenReturn(vo);
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
}
