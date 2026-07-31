package com.ulticode.modules.admin.projection;

import com.ulticode.auth.api.dto.AuthorizationSnapshotDTO;
import com.ulticode.auth.api.dto.PermissionEntry;
import com.ulticode.auth.api.service.RoleTemplateService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.dto.AdminUserQueryDTO;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.modules.admin.port.AdminUserStatsReadPort;
import com.ulticode.modules.auth.service.AuthCutoverService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DefaultAdminUserProjection} &mdash; the read-side deep
 * module for the admin user surface.
 *
 * <p>After P7-RETIRE-PERMISSION-001, permissions are read via Dubbo RPC
 * ({@link RoleTemplateService}) and {@link AuthCutoverService#getSnapshot}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultAdminUserProjection")
class AdminUserProjectionTest {

    @Mock private UserMapper userMapper;
    @Mock private AdminUserStatsReadPort userStatsReadPort;
    @Mock private AuthCutoverService authCutoverService;
    @Mock private RoleTemplateService roleTemplateService;

    private DefaultAdminUserProjection projection;

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

    @BeforeEach
    void setUp() {
        projection = new DefaultAdminUserProjection(
                userMapper, userStatsReadPort, authCutoverService);
        // Inject Dubbo field manually (no Spring context in unit test)
        ReflectionTestUtils.setField(projection, "roleTemplateService", roleTemplateService);
    }

    @Nested
    @DisplayName("getUsers() — list path skips stats / permissions enrichment")
    class GetUsers {

        @Test
        @DisplayName("does not trigger stats or permission queries for list view")
        void doesNotTriggerExtraQueries() {
            User user = createValidUser();
            Page<User> page = new Page<>();
            page.setRecords(List.of(user));
            page.setTotal(1);
            when(userMapper.selectPage(any(Page.class), any())).thenReturn(page);

            projection.getUsers(new AdminUserQueryDTO());

            verifyNoInteractions(userStatsReadPort, authCutoverService, roleTemplateService);
        }
    }

    @Nested
    @DisplayName("getUserById() — detail path enriches stats + permissions")
    class GetUserById {

        @Test
        @DisplayName("populates stats correctly when port returns values")
        void populatesStatsCorrectly() {
            User user = createValidUser();
            when(userMapper.selectById("user-123")).thenReturn(user);
            stubStats("user-123", 10L, 5L, 3L, 7);
            when(roleTemplateService.getRoleTemplate("ADMIN"))
                    .thenReturn(RpcResult.success(List.of(), "t-test"));
            when(authCutoverService.getSnapshot("user-123"))
                    .thenReturn(snapshotWithEntries(List.of()));

            AdminUserVO result = projection.getUserById("user-123");

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
            User user = createValidUser();
            when(userMapper.selectById("user-123")).thenReturn(user);
            stubStats("user-123", 0L, 0L, 0L, 0);
            when(roleTemplateService.getRoleTemplate("ADMIN"))
                    .thenReturn(RpcResult.success(List.of(), "t-test"));
            when(authCutoverService.getSnapshot("user-123"))
                    .thenReturn(snapshotWithEntries(List.of()));

            AdminUserVO result = projection.getUserById("user-123");

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

            PermissionEntry roleEntry = new PermissionEntry("READ", "USER", "role", null);
            when(roleTemplateService.getRoleTemplate("ADMIN"))
                    .thenReturn(RpcResult.success(List.of(roleEntry), "t-test"));

            PermissionEntry directEntry = new PermissionEntry("DELETE", "PROBLEM", "direct", null);
            when(authCutoverService.getSnapshot("user-123"))
                    .thenReturn(snapshotWithEntries(List.of(directEntry)));

            AdminUserVO result = projection.getUserById("user-123");

            assertThat(result).isNotNull();
            assertThat(result.getPermissions()).hasSize(2);
            assertThat(result.getPermissions())
                    .anySatisfy(p -> {
                        assertThat(p.getSource()).isEqualTo("role");
                        assertThat(p.getAction()).isEqualTo("READ");
                    })
                    .anySatisfy(p -> {
                        assertThat(p.getSource()).isEqualTo("direct");
                        assertThat(p.getAction()).isEqualTo("DELETE");
                    });
        }

        @Test
        @DisplayName("filters out expired direct permissions from VO")
        void filtersExpiredPermissions() {
            User user = createValidUser();
            when(userMapper.selectById("user-123")).thenReturn(user);
            stubStats("user-123", 0L, 0L, 0L, 0);
            when(roleTemplateService.getRoleTemplate("ADMIN"))
                    .thenReturn(RpcResult.success(List.of(), "t-test"));

            OffsetDateTime expired = LocalDateTime.now().minusMinutes(1).atOffset(ZoneOffset.UTC);
            OffsetDateTime active = LocalDateTime.now().plusHours(1).atOffset(ZoneOffset.UTC);

            PermissionEntry expiredEntry = new PermissionEntry("CREATE", "PROBLEM", "direct", expired);
            PermissionEntry activeEntry = new PermissionEntry("READ", "USER", "direct", active);
            PermissionEntry permanentEntry = new PermissionEntry("UPDATE", "SOLUTION", "direct", null);

            when(authCutoverService.getSnapshot("user-123"))
                    .thenReturn(snapshotWithEntries(List.of(expiredEntry, activeEntry, permanentEntry)));

            AdminUserVO result = projection.getUserById("user-123");

            assertThat(result).isNotNull();
            assertThat(result.getPermissions()).hasSize(2);
            assertThat(result.getPermissions())
                .extracting("action")
                .containsExactlyInAnyOrder("READ", "UPDATE");
        }

        @Test
        @DisplayName("throws BusinessException when user not found")
        void userNotFound_throwsBusinessException() {
            when(userMapper.selectById("nonexistent")).thenReturn(null);

            assertThatThrownBy(() -> projection.getUserById("nonexistent"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
                    });
        }
    }

    private AuthorizationSnapshotDTO snapshotWithEntries(List<PermissionEntry> entries) {
        return new AuthorizationSnapshotDTO("user-123", "ADMIN", Set.of(), 0L, entries);
    }
}
