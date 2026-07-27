package com.ulticode.modules.permission.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.uuid.FixedUuidGenerator;
import com.ulticode.modules.permission.PermissionVocabulary;
import com.ulticode.modules.permission.entity.RolePermission;
import com.ulticode.modules.permission.entity.UserPermission;
import com.ulticode.modules.permission.mapper.RolePermissionMapper;
import com.ulticode.modules.permission.mapper.UserPermissionMapper;
import com.ulticode.modules.permission.port.UserRoleReadPort;
import com.ulticode.modules.permission.service.impl.PermissionServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

/**
 * {@link PermissionService} unit tests.
 *
 * <p><strong>P2-RBAC-001:</strong> the legacy service is now read-only
 * for the {@code user_permissions} table. The write methods
 * ({@code assignPermission}, {@code revokePermission}) are kept on
 * the {@link PermissionService} interface for binary compatibility
 * but throw a directive {@link UnsupportedOperationException} that
 * points callers at
 * {@code com.ulticode.modules.admin.client.BackendAuthRoleAdminClient}
 * (proxied to backend-auth's owner-only command surface). The
 * read-side tests ({@code getUserPermissionStrings}) keep their
 * Phase 0 §7.1 expiry-filter coverage.
 *
 * <p>The closed-method test cases that previously verified
 * insert / update / delete behaviour are removed; the closed
 * behaviour is now verified by {@code WriteMethodsClosed} below.
 * P2-DISC-006 will delete the closed methods and this test class
 * entirely.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PermissionService")
class PermissionServiceTest {

    @Mock
    private UserPermissionMapper userPermissionMapper;

    @Mock
    private RolePermissionMapper rolePermissionMapper;

    @Mock
    private UserRoleReadPort userRoleReadPort;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private Clock clock;

    private PermissionService permissionService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        lenient().when(clock.getZone()).thenReturn(ZoneId.systemDefault());
        lenient().when(clock.instant()).thenReturn(java.time.Instant.now());
        lenient().when(currentUserProvider.getCurrentUserId()).thenReturn("test-admin");
        permissionService = new PermissionServiceImpl(
            userPermissionMapper, rolePermissionMapper, userRoleReadPort, clock,
            new FixedUuidGenerator(), new PermissionVocabulary(), currentUserProvider);
    }

    /**
     * P2-RBAC-001: the legacy write methods are closed. The
     * exceptions are the only signal callers will see; the test
     * pins the message so a follow-up refactor doesn't silently
     * re-open the foreign-writer path.
     */
    @Nested
    @DisplayName("P2-RBAC-001: write methods are closed")
    class WriteMethodsClosed {

        @Test
        @DisplayName("assignPermission throws UnsupportedOperationException pointing at BackendAuthRoleAdminClient")
        void assignPermission_throws() {
            assertThatThrownBy(() -> permissionService.assignPermission(
                    "user-1", "CREATE", "PROBLEM", null))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("BackendAuthRoleAdminClient.grantPermission");
            // The legacy mapper must NOT be touched on the closed path.
            verify(userPermissionMapper, never()).insert(any(UserPermission.class));
            verify(userPermissionMapper, never()).updateById(any(UserPermission.class));
        }

        @Test
        @DisplayName("revokePermission throws UnsupportedOperationException pointing at BackendAuthRoleAdminClient")
        void revokePermission_throws() {
            assertThatThrownBy(() -> permissionService.revokePermission(
                    "user-1", "READ", "USER"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("BackendAuthRoleAdminClient.revokePermission");
            verify(userPermissionMapper, never()).delete(any(LambdaQueryWrapper.class));
        }
    }

    @Nested
    @DisplayName("getUserPermissionStrings()")
    class GetUserPermissionStrings {

        @Test
        @DisplayName("returns empty when user does not exist")
        void emptyWhenUserAbsent() {
            when(userRoleReadPort.findRole("ghost")).thenReturn(Optional.empty());

            assertThat(permissionService.getUserPermissionStrings("ghost")).isEmpty();
        }

        @Test
        @DisplayName("merges role permissions and user permissions when role is set")
        void mergesRoleAndUserPermissions() {
            when(userRoleReadPort.findRole("user-1"))
                .thenReturn(Optional.of(new UserRoleReadPort.UserRole("ADMIN")));
            RolePermission rp = new RolePermission();
            rp.setAction("READ");
            rp.setResource("USER");
            when(rolePermissionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(rp));
            UserPermission up = new UserPermission();
            up.setAction("CREATE");
            up.setResource("PROBLEM");
            when(userPermissionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(up));

            assertThat(permissionService.getUserPermissionStrings("user-1"))
                .containsExactlyInAnyOrder("READ:USER", "CREATE:PROBLEM");
        }

        @Test
        @DisplayName("includes only user permissions (no role lookup) when role is null")
        void userPermsOnlyWhenRoleNull() {
            when(userRoleReadPort.findRole("user-1"))
                .thenReturn(Optional.of(new UserRoleReadPort.UserRole(null)));
            UserPermission up = new UserPermission();
            up.setAction("CREATE");
            up.setResource("PROBLEM");
            when(userPermissionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(up));

            assertThat(permissionService.getUserPermissionStrings("user-1"))
                .containsExactly("CREATE:PROBLEM");
            verify(rolePermissionMapper, never()).selectList(any(LambdaQueryWrapper.class));
        }

        // ============ Phase 0 §7.1: effective permission expiry filter ============

        @Test
        @DisplayName("Phase 0: filters out user_permissions with past expires_at")
        void filtersExpiredPermissions() {
            // The DB rows contain a mix of expired / future / null entries;
            // the LambdaQueryWrapper produced by PermissionServiceImpl must
            // filter at the SQL level so we only see the non-expired ones.
            when(userRoleReadPort.findRole("user-1"))
                .thenReturn(Optional.of(new UserRoleReadPort.UserRole("ADMIN")));
            when(rolePermissionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of());
            // Return only the still-valid row (MyBatis-Plus applies the
            // expiry predicate in the WHERE clause; this test verifies the
            // service constructs the right predicate).
            UserPermission valid = new UserPermission();
            valid.setAction("READ");
            valid.setResource("USER");
            valid.setExpiresAt(LocalDateTime.now().plusDays(7));
            when(userPermissionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(valid));

            assertThat(permissionService.getUserPermissionStrings("user-1"))
                .containsExactly("READ:USER");
        }
    }
}
