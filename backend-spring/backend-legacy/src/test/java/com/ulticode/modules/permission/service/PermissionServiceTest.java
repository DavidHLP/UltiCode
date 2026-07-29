package com.ulticode.modules.permission.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.permission.entity.RolePermission;
import com.ulticode.modules.permission.entity.UserPermission;
import com.ulticode.modules.permission.mapper.RolePermissionMapper;
import com.ulticode.modules.permission.mapper.UserPermissionMapper;
import com.ulticode.modules.permission.port.UserRoleReadPort;
import com.ulticode.modules.permission.service.impl.PermissionServiceImpl;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PermissionService} unit tests.
 *
 * <p><strong>P2-DISC-006:</strong> legacy write methods assignPermission / revokePermission
 * are removed. Read-side tests ({@code getUserPermissionStrings}) preserve Phase 0 §7.1 expiry-filter coverage.
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
    private Clock clock;

    private PermissionService permissionService;

    @BeforeEach
    void setUp() {
        lenient().when(clock.getZone()).thenReturn(ZoneId.systemDefault());
        lenient().when(clock.instant()).thenReturn(java.time.Instant.now());
        permissionService = new PermissionServiceImpl(
            userPermissionMapper, rolePermissionMapper, userRoleReadPort, clock);
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
            when(userRoleReadPort.findRole("user-1"))
                .thenReturn(Optional.of(new UserRoleReadPort.UserRole("ADMIN")));
            when(rolePermissionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of());
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
