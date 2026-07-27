package com.ulticode.modules.admin.service.impl;

import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.admin.client.BackendAuthRoleAdminClient;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.modules.admin.projection.AdminUserProjection;
import com.ulticode.modules.permission.PermissionVocabulary;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * {@link UserPermissionServiceImpl} unit tests.
 *
 * <p>P2-RBAC-001: the legacy no longer delegates to the local
 * {@code PermissionService} for write paths (that would be a
 * foreign write to {@code user_permissions}); it forwards every
 * grant / revoke through {@link BackendAuthRoleAdminClient} to
 * backend-auth's owner-only command surface. The before-snapshot
 * for the audit comes from {@link AdminUserProjection}, which is
 * the single read-side seam (per ADR-0011 Stage 2).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserPermissionServiceImpl")
class UserPermissionServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private BackendAuthRoleAdminClient backendAuthRoleAdminClient;

    @Mock
    private AdminUserProjection adminUserProjection;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private Clock clock;

    private UserPermissionServiceImpl userPermissionService;

    @BeforeEach
    void setUp() {
        lenient().when(clock.getZone()).thenReturn(ZoneId.systemDefault());
        lenient().when(clock.instant()).thenReturn(java.time.Instant.now());
        // SUPER_ADMIN role for the requireSuperAdminForManagePermissionsSystem
        // guard (assignUserPermission / revokeUserPermission tests use
        // MANAGE_PERMISSIONS:SYSTEM which is super-admin-only).
        lenient().when(currentUserProvider.hasRole("SUPER_ADMIN")).thenReturn(true);
        lenient().when(currentUserProvider.getCurrentUserId()).thenReturn("test-super-admin");
        userPermissionService = new UserPermissionServiceImpl(
                userMapper, backendAuthRoleAdminClient, adminUserProjection, clock,
                new PermissionVocabulary(), currentUserProvider);
    }

    private User createValidUser() {
        User user = new User();
        user.setId("user-123");
        user.setUsername("testuser");
        user.setName("Test User");
        user.setEmail("test@example.com");
        user.setRole("USER");
        user.setIsActive(true);
        user.setIsBanned(false);
        return user;
    }

    /**
     * Helper: a fresh {@link AdminUserVO} with an empty permission
     * list (no direct user_permissions for the target user). The
     * {@code assignUserPermission} / {@code revokeUserPermission}
     * tests use this as the before-snapshot returned by
     * {@code AdminUserProjection}.
     */
    private AdminUserVO emptyPermissionsVo(String userId) {
        AdminUserVO vo = new AdminUserVO();
        vo.setId(userId);
        vo.setPermissions(List.of());
        return vo;
    }

    @Nested
    @DisplayName("assignUserPermission()")
    class AssignUserPermission {

        @Test
        @DisplayName("forwards to BackendAuthRoleAdminClient and returns VO via AdminUserProjection")
        void grantNew_delegatesAndReturnsVO() {
            User user = createValidUser();
            when(userMapper.selectById("user-123")).thenReturn(user);
            when(adminUserProjection.getUserById("user-123"))
                    .thenReturn(emptyPermissionsVo("user-123"));

            AdminUserVO expectedVo = new AdminUserVO();
            expectedVo.setId("user-123");
            when(adminUserProjection.getUserById("user-123"))
                    .thenReturn(emptyPermissionsVo("user-123"))
                    .thenReturn(expectedVo);

            AdminUserVO vo = userPermissionService.assignUserPermission(
                    "user-123", "MANAGE_PERMISSIONS", "SYSTEM", null);

            assertThat(vo).isNotNull();
            assertThat(vo.getId()).isEqualTo("user-123");
            // P2-RBAC-001: the actual write must be the client, not the
            // legacy local PermissionService (which was the foreign writer).
            verify(backendAuthRoleAdminClient).grantPermission(
                    eq("user-123"), eq("MANAGE_PERMISSIONS"), eq("SYSTEM"), any());
            verify(adminUserProjection, times(2)).getUserById("user-123");
        }

        @Test
        @DisplayName("throws USER_NOT_FOUND when user does not exist")
        void userMissing_throws() {
            when(userMapper.selectById("nope")).thenReturn(null);

            assertThatThrownBy(() -> userPermissionService.assignUserPermission(
                    "nope", "READ", "USER", null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_FOUND));

            verify(backendAuthRoleAdminClient, never())
                    .grantPermission(any(), any(), any(), any());
            verify(adminUserProjection, never()).getUserById(any());
        }
    }

    @Nested
    @DisplayName("revokeUserPermission()")
    class RevokeUserPermission {

        @Test
        @DisplayName("forwards revoke to BackendAuthRoleAdminClient and returns VO when permission exists")
        void revokeExisting_delegates() {
            User user = createValidUser();
            when(userMapper.selectById("user-123")).thenReturn(user);
            // before-snapshot: existing direct permission READ:USER for the actor
            AdminUserVO.PermissionInfo existing = new AdminUserVO.PermissionInfo();
            existing.setAction("READ");
            existing.setResource("USER");
            AdminUserVO before = new AdminUserVO();
            before.setId("user-123");
            before.setPermissions(List.of(existing));
            when(adminUserProjection.getUserById("user-123"))
                    .thenReturn(before)
                    .thenReturn(emptyPermissionsVo("user-123"));

            AdminUserVO vo = userPermissionService.revokeUserPermission(
                    "user-123", "READ", "USER");

            assertThat(vo).isNotNull();
            verify(backendAuthRoleAdminClient).revokePermission(
                    "user-123", "READ", "USER");
            verify(adminUserProjection, times(2)).getUserById("user-123");
        }

        @Test
        @DisplayName("returns VO without throwing when permission did not exist (idempotent)")
        void revokeMissing_doesNotThrow() {
            User user = createValidUser();
            when(userMapper.selectById("user-123")).thenReturn(user);
            when(adminUserProjection.getUserById("user-123"))
                    .thenReturn(emptyPermissionsVo("user-123"))
                    .thenReturn(emptyPermissionsVo("user-123"));

            AdminUserVO vo = userPermissionService.revokeUserPermission(
                    "user-123", "READ", "USER");

            assertThat(vo).isNotNull();
            verify(backendAuthRoleAdminClient).revokePermission(
                    "user-123", "READ", "USER");
        }

        @Test
        @DisplayName("throws USER_NOT_FOUND when user does not exist")
        void userMissing_throws() {
            when(userMapper.selectById("nope")).thenReturn(null);

            assertThatThrownBy(() -> userPermissionService.revokeUserPermission(
                    "nope", "READ", "USER"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_FOUND));

            verify(backendAuthRoleAdminClient, never())
                    .revokePermission(any(), any(), any());
            verify(adminUserProjection, never()).getUserById(any());
        }
    }

    @Nested
    @DisplayName("HIGH-1 guard: MANAGE_PERMISSIONS:SYSTEM")
    class ManagePermissionsSystemGuard {

        @BeforeEach
        void downgradeToAdmin() {
            // Demote the actor to plain ADMIN (no SUPER_ADMIN) so the guard rejects.
            // The outer @BeforeEach stubbed hasRole("SUPER_ADMIN")→true; we override here.
            when(currentUserProvider.hasRole("SUPER_ADMIN")).thenReturn(false);
        }

        @Test
        @DisplayName("rejects ADMIN attempting to grant MANAGE_PERMISSIONS:SYSTEM")
        void adminCannotGrantManagePermissionsSystem() {
            assertThatThrownBy(() -> userPermissionService.assignUserPermission(
                    "user-123", "MANAGE_PERMISSIONS", "SYSTEM", null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.FORBIDDEN));

            verify(backendAuthRoleAdminClient, never())
                    .grantPermission(any(), any(), any(), any());
        }

        @Test
        @DisplayName("rejects ADMIN attempting to revoke MANAGE_PERMISSIONS:SYSTEM")
        void adminCannotRevokeManagePermissionsSystem() {
            assertThatThrownBy(() -> userPermissionService.revokeUserPermission(
                    "user-123", "MANAGE_PERMISSIONS", "SYSTEM"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.FORBIDDEN));

            verify(backendAuthRoleAdminClient, never())
                    .revokePermission(any(), any(), any());
        }
    }
}
