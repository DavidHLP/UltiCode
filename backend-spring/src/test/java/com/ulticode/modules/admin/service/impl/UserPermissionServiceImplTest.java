package com.ulticode.modules.admin.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.modules.admin.service.UserManagementService;
import com.ulticode.modules.permission.entity.UserPermission;
import com.ulticode.modules.permission.service.PermissionService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * {@link UserPermissionServiceImpl} 单元测试。
 *
 * <p>从原 {@code AdminUserServiceImplTest} 拆分而来（架构评审 Candidate 1）：
 * 授权 / 撤销相关用例归属本测试；
 * 用户档案 / 封禁 / 批量操作相关用例移至 {@link UserManagementServiceImplTest}。
 *
 * <p>{@link UserManagementService} 以 mock 注入，验证拆分后两服务通过
 * {@link UserManagementService#getUserById(String)} 协作的契约。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserPermissionServiceImpl")
class UserPermissionServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PermissionService permissionService;

    @Mock
    private UserManagementService userManagementService;

    private UserPermissionServiceImpl userPermissionService;

    @BeforeEach
    void setUp() {
        userPermissionService = new UserPermissionServiceImpl(
                userMapper, permissionService, userManagementService);
        // 注入 SUPER_ADMIN 安全上下文,让 requireSuperAdminForManagePermissionsSystem 守卫通过
        // (assignUserPermission / revokeUserPermission 的现有测试用 MANAGE_PERMISSIONS:SYSTEM)
        Authentication auth = new UsernamePasswordAuthenticationToken(
            "test-super-admin", "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
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

    @Nested
    @DisplayName("assignUserPermission()")
    class AssignUserPermission {

        @Test
        @DisplayName("delegates to PermissionService and returns VO via UserManagementService")
        void grantNew_delegatesAndReturnsVO() {
            User user = createValidUser();
            when(userMapper.selectById("user-123")).thenReturn(user);
            // before-snapshot: empty
            when(permissionService.getUserPermissions("user-123")).thenReturn(List.of());
            when(permissionService.assignPermission(eq("user-123"), eq("MANAGE_PERMISSIONS"),
                    eq("SYSTEM"), any())).thenReturn(new UserPermission());

            AdminUserVO expectedVo = new AdminUserVO();
            expectedVo.setId("user-123");
            when(userManagementService.getUserById("user-123")).thenReturn(expectedVo);

            AdminUserVO vo = userPermissionService.assignUserPermission(
                    "user-123", "MANAGE_PERMISSIONS", "SYSTEM", null);

            assertThat(vo).isNotNull();
            assertThat(vo.getId()).isEqualTo("user-123");
            verify(permissionService).assignPermission("user-123",
                    "MANAGE_PERMISSIONS", "SYSTEM", null);
            verify(userManagementService).getUserById("user-123");
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

            verify(permissionService, never()).assignPermission(any(), any(), any(), any());
            verify(userManagementService, never()).getUserById(any());
        }
    }

    @Nested
    @DisplayName("revokeUserPermission()")
    class RevokeUserPermission {

        @Test
        @DisplayName("delegates and returns VO when permission exists")
        void revokeExisting_delegates() {
            User user = createValidUser();
            when(userMapper.selectById("user-123")).thenReturn(user);
            when(permissionService.getUserPermissions("user-123")).thenReturn(List.of());
            when(permissionService.revokePermission("user-123", "READ", "USER")).thenReturn(true);

            AdminUserVO expectedVo = new AdminUserVO();
            expectedVo.setId("user-123");
            when(userManagementService.getUserById("user-123")).thenReturn(expectedVo);

            AdminUserVO vo = userPermissionService.revokeUserPermission(
                    "user-123", "READ", "USER");

            assertThat(vo).isNotNull();
            verify(permissionService).revokePermission("user-123", "READ", "USER");
            verify(userManagementService).getUserById("user-123");
        }

        @Test
        @DisplayName("returns VO without throwing when permission did not exist (REST idempotent)")
        void revokeMissing_doesNotThrow() {
            User user = createValidUser();
            when(userMapper.selectById("user-123")).thenReturn(user);
            when(permissionService.getUserPermissions("user-123")).thenReturn(List.of());
            when(permissionService.revokePermission("user-123", "READ", "USER")).thenReturn(false);

            AdminUserVO expectedVo = new AdminUserVO();
            expectedVo.setId("user-123");
            when(userManagementService.getUserById("user-123")).thenReturn(expectedVo);

            AdminUserVO vo = userPermissionService.revokeUserPermission(
                    "user-123", "READ", "USER");

            assertThat(vo).isNotNull();
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

            verify(permissionService, never()).revokePermission(any(), any(), any());
            verify(userManagementService, never()).getUserById(any());
        }
    }

    @Nested
    @DisplayName("HIGH-1 guard: MANAGE_PERMISSIONS:SYSTEM")
    class ManagePermissionsSystemGuard {

        @BeforeEach
        void clearAuth() {
            // 显式降级为普通 ADMIN,验证守卫拒绝
            SecurityContextHolder.clearContext();
            Authentication adminAuth = new UsernamePasswordAuthenticationToken(
                "test-admin", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            SecurityContextHolder.getContext().setAuthentication(adminAuth);
        }

        @Test
        @DisplayName("rejects ADMIN attempting to grant MANAGE_PERMISSIONS:SYSTEM")
        void adminCannotGrantManagePermissionsSystem() {
            assertThatThrownBy(() -> userPermissionService.assignUserPermission(
                    "user-123", "MANAGE_PERMISSIONS", "SYSTEM", null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.FORBIDDEN));

            verify(permissionService, never()).assignPermission(any(), any(), any(), any());
        }

        @Test
        @DisplayName("rejects ADMIN attempting to revoke MANAGE_PERMISSIONS:SYSTEM")
        void adminCannotRevokeManagePermissionsSystem() {
            assertThatThrownBy(() -> userPermissionService.revokeUserPermission(
                    "user-123", "MANAGE_PERMISSIONS", "SYSTEM"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.FORBIDDEN));

            verify(permissionService, never()).revokePermission(any(), any(), any());
        }
    }
}
