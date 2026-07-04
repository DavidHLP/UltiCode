package com.ulticode.modules.admin.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.util.AuditHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.modules.admin.dto.AdminUserQueryDTO;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.modules.admin.port.AdminUserStatsReadPort;
import com.ulticode.modules.permission.entity.RolePermission;
import com.ulticode.modules.permission.entity.UserPermission;
import com.ulticode.modules.permission.mapper.RolePermissionMapper;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserServiceImpl")
class AdminUserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditHelper auditHelper;

    @Mock
    private AdminUserStatsReadPort userStatsReadPort;

    @Mock
    private PermissionService permissionService;

    @Mock
    private RolePermissionMapper rolePermissionMapper;

    private AdminUserServiceImpl adminUserService;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserServiceImpl(
                userMapper, passwordEncoder, auditHelper,
                userStatsReadPort, permissionService, rolePermissionMapper);
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
        user.setRole("ADMIN");
        user.setIsActive(true);
        user.setIsBanned(false);
        return user;
    }

    private void stubStats(String userId, long sub, long accepted, long solutions, int streak) {
        when(userStatsReadPort.countSubmissionsByUserId(userId)).thenReturn(sub);
        when(userStatsReadPort.countAcceptedProblemsByUserId(userId)).thenReturn(accepted);
        when(userStatsReadPort.countSolutionsByUserId(userId)).thenReturn(solutions);
        when(userStatsReadPort.calculateSubmissionStreak(userId)).thenReturn(streak);
    }

    @Nested
    @DisplayName("getUsers()")
    class GetUsers {

        @Test
        @DisplayName("does not trigger stats or permission queries for list view")
        void doesNotTriggerExtraQueries() {
            User user = createValidUser();
            Page<User> page = new Page<>();
            page.setRecords(List.of(user));
            page.setTotal(1);
            when(userMapper.selectPage(any(Page.class), any())).thenReturn(page);

            adminUserService.getUsers(new AdminUserQueryDTO());

            verifyNoInteractions(userStatsReadPort, rolePermissionMapper, permissionService);
        }
    }

    @Nested
    @DisplayName("getUserById()")
    class GetUserById {

        @Test
        @DisplayName("populates stats correctly when port returns values")
        void populatesStatsCorrectly() {
            User user = createValidUser();
            when(userMapper.selectById("user-123")).thenReturn(user);
            stubStats("user-123", 10L, 5L, 3L, 7);
            when(rolePermissionMapper.selectList(any())).thenReturn(List.of());
            when(permissionService.getUserPermissions("user-123")).thenReturn(List.of());

            AdminUserVO result = adminUserService.getUserById("user-123");

            assertThat(result).isNotNull();
            assertThat(result.getStats()).isNotNull();
            assertThat(result.getStats().getTotalSubmissions()).isEqualTo(10);
            assertThat(result.getStats().getAcceptedSubmissions()).isEqualTo(5);
            assertThat(result.getStats().getTotalSolutions()).isEqualTo(3);
            assertThat(result.getStats().getStreak()).isEqualTo(7);
        }

        @Test
        @DisplayName("defaults stats to zero when port returns zero")
        void portZero_defaultsToZero() {
            // null→0 降级由 AdminUserStatsReadAdapter 拥有 (adapter 测试覆盖);
            // ServiceImpl 只看到非 null 基本类型,这里验证 port 返回 0 时 VO 也为 0。
            User user = createValidUser();
            when(userMapper.selectById("user-123")).thenReturn(user);
            stubStats("user-123", 0L, 0L, 0L, 0);
            when(rolePermissionMapper.selectList(any())).thenReturn(List.of());
            when(permissionService.getUserPermissions("user-123")).thenReturn(List.of());

            AdminUserVO result = adminUserService.getUserById("user-123");

            assertThat(result).isNotNull();
            assertThat(result.getStats()).isNotNull();
            assertThat(result.getStats().getTotalSubmissions()).isEqualTo(0);
            assertThat(result.getStats().getAcceptedSubmissions()).isEqualTo(0);
            assertThat(result.getStats().getTotalSolutions()).isEqualTo(0);
            assertThat(result.getStats().getStreak()).isEqualTo(0);
        }

        @Test
        @DisplayName("populates permissions with role and direct permissions")
        void populatesPermissionsCorrectly() {
            User user = createValidUser();
            when(userMapper.selectById("user-123")).thenReturn(user);
            stubStats("user-123", 0L, 0L, 0L, 0);

            RolePermission rolePerm = new RolePermission();
            rolePerm.setAction("read");
            rolePerm.setResource("users");
            when(rolePermissionMapper.selectList(any())).thenReturn(List.of(rolePerm));

            UserPermission directPerm = new UserPermission();
            directPerm.setAction("write");
            directPerm.setResource("problems");
            when(permissionService.getUserPermissions("user-123")).thenReturn(List.of(directPerm));

            AdminUserVO result = adminUserService.getUserById("user-123");

            assertThat(result).isNotNull();
            assertThat(result.getPermissions()).hasSize(2);
            assertThat(result.getPermissions().get(0).getSource()).isEqualTo("role");
            assertThat(result.getPermissions().get(0).getAction()).isEqualTo("read");
            assertThat(result.getPermissions().get(1).getSource()).isEqualTo("direct");
            assertThat(result.getPermissions().get(1).getAction()).isEqualTo("write");
        }

        @Test
        @DisplayName("MEDIUM-3: filters out expired direct permissions from VO")
        void filtersExpiredPermissions() {
            User user = createValidUser();
            when(userMapper.selectById("user-123")).thenReturn(user);
            stubStats("user-123", 0L, 0L, 0L, 0);
            when(rolePermissionMapper.selectList(any())).thenReturn(List.of());

            UserPermission expired = new UserPermission();
            expired.setAction("CREATE");
            expired.setResource("PROBLEM");
            expired.setExpiresAt(java.time.LocalDateTime.now().minusMinutes(1));

            UserPermission active = new UserPermission();
            active.setAction("READ");
            active.setResource("USER");
            active.setExpiresAt(java.time.LocalDateTime.now().plusHours(1));

            UserPermission permanent = new UserPermission();
            permanent.setAction("UPDATE");
            permanent.setResource("SOLUTION");
            // null expiresAt = 永久

            when(permissionService.getUserPermissions("user-123"))
                .thenReturn(List.of(expired, active, permanent));

            AdminUserVO result = adminUserService.getUserById("user-123");

            assertThat(result).isNotNull();
            assertThat(result.getPermissions()).hasSize(2);
            // 过期权限被过滤,顺序由 source 决定
            assertThat(result.getPermissions())
                .extracting("action")
                .containsExactlyInAnyOrder("READ", "UPDATE");
            // 同时验证 expiresAt 被正确传递(非 null 字段)
            assertThat(result.getPermissions())
                .filteredOn(p -> "READ".equals(p.getAction()))
                .extracting("expiresAt")
                .containsExactly((Object) active.getExpiresAt());
        }

        @Test
        @DisplayName("throws BusinessException when user not found")
        void userNotFound_throwsBusinessException() {
            when(userMapper.selectById("nonexistent")).thenReturn(null);

            assertThatThrownBy(() -> adminUserService.getUserById("nonexistent"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
                    });
        }
    }

    @Nested
    @DisplayName("assignUserPermission()")
    class AssignUserPermission {

        @Test
        @DisplayName("delegates to PermissionService and returns VO with permissions populated")
        void grantNew_delegatesAndReturnsVO() {
            User user = createValidUser();
            user.setRole("USER");
            when(userMapper.selectById("user-123")).thenReturn(user);
            // before-snapshot: empty
            when(permissionService.getUserPermissions("user-123")).thenReturn(List.of());
            when(permissionService.assignPermission(eq("user-123"), eq("MANAGE_PERMISSIONS"),
                    eq("SYSTEM"), any())).thenReturn(new UserPermission());
            stubStats("user-123", 0L, 0L, 0L, 0);
            when(rolePermissionMapper.selectList(any())).thenReturn(List.of());

            AdminUserVO vo = adminUserService.assignUserPermission(
                    "user-123", "MANAGE_PERMISSIONS", "SYSTEM", null);

            assertThat(vo).isNotNull();
            assertThat(vo.getId()).isEqualTo("user-123");
            verify(permissionService).assignPermission("user-123",
                    "MANAGE_PERMISSIONS", "SYSTEM", null);
        }

        @Test
        @DisplayName("throws USER_NOT_FOUND when user does not exist")
        void userMissing_throws() {
            when(userMapper.selectById("nope")).thenReturn(null);

            assertThatThrownBy(() -> adminUserService.assignUserPermission(
                    "nope", "READ", "USER", null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_FOUND));

            verify(permissionService, never()).assignPermission(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("revokeUserPermission()")
    class RevokeUserPermission {

        @Test
        @DisplayName("delegates and returns VO when permission exists")
        void revokeExisting_delegates() {
            User user = createValidUser();
            user.setRole("USER");
            when(userMapper.selectById("user-123")).thenReturn(user);
            when(permissionService.getUserPermissions("user-123")).thenReturn(List.of());
            when(permissionService.revokePermission("user-123", "READ", "USER")).thenReturn(true);
            stubStats("user-123", 0L, 0L, 0L, 0);
            when(rolePermissionMapper.selectList(any())).thenReturn(List.of());

            AdminUserVO vo = adminUserService.revokeUserPermission(
                    "user-123", "READ", "USER");

            assertThat(vo).isNotNull();
            verify(permissionService).revokePermission("user-123", "READ", "USER");
        }

        @Test
        @DisplayName("returns VO without throwing when permission did not exist (REST idempotent)")
        void revokeMissing_doesNotThrow() {
            User user = createValidUser();
            user.setRole("USER");
            when(userMapper.selectById("user-123")).thenReturn(user);
            when(permissionService.getUserPermissions("user-123")).thenReturn(List.of());
            when(permissionService.revokePermission("user-123", "READ", "USER")).thenReturn(false);
            stubStats("user-123", 0L, 0L, 0L, 0);
            when(rolePermissionMapper.selectList(any())).thenReturn(List.of());

            AdminUserVO vo = adminUserService.revokeUserPermission(
                    "user-123", "READ", "USER");

            assertThat(vo).isNotNull();
        }

        @Test
        @DisplayName("throws USER_NOT_FOUND when user does not exist")
        void userMissing_throws() {
            when(userMapper.selectById("nope")).thenReturn(null);

            assertThatThrownBy(() -> adminUserService.revokeUserPermission(
                    "nope", "READ", "USER"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_FOUND));

            verify(permissionService, never()).revokePermission(any(), any(), any());
        }
    }
}
