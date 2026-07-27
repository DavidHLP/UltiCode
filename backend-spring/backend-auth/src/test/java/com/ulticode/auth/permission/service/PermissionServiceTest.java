package com.ulticode.auth.permission.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.permission.PermissionVocabulary;
import com.ulticode.auth.permission.entity.RolePermission;
import com.ulticode.auth.permission.entity.UserPermission;
import com.ulticode.auth.permission.mapper.RolePermissionMapper;
import com.ulticode.auth.permission.mapper.UserPermissionMapper;
import com.ulticode.auth.permission.port.UserRoleReadPort;
import com.ulticode.auth.permission.service.impl.PermissionServiceImpl;
import com.ulticode.auth.security.CurrentUserProvider;
import com.ulticode.auth.util.FixedUuidGenerator;
import com.ulticode.common.error.BaseErrorCode;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PermissionService (backend-auth)")
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

    @BeforeEach
    void setUp() {
        lenient().when(clock.getZone()).thenReturn(ZoneId.systemDefault());
        lenient().when(clock.instant()).thenReturn(Instant.now());
        lenient().when(currentUserProvider.getCurrentUserId()).thenReturn("test-admin");
        permissionService = new PermissionServiceImpl(
                userPermissionMapper, rolePermissionMapper, userRoleReadPort, clock,
                new FixedUuidGenerator(), new PermissionVocabulary(), currentUserProvider);
    }

    @Nested
    @DisplayName("assignPermission()")
    class AssignPermission {

        @Test
        @DisplayName("inserts new row when no existing record")
        void insertsNewRow() {
            when(userPermissionMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(null);

            UserPermission result = permissionService.assignPermission(
                    "user-1", "CREATE", "PROBLEM", null);

            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo("user-1");
            assertThat(result.getAction()).isEqualTo("CREATE");
            assertThat(result.getResource()).isEqualTo("PROBLEM");
            assertThat(result.getId()).isNotBlank();
            verify(userPermissionMapper, times(1)).insert(any(UserPermission.class));
            verify(userPermissionMapper, never()).updateById(any(UserPermission.class));
        }

        @Test
        @DisplayName("updates existing row when (userId, action, resource) collides")
        void updatesExistingRow() {
            UserPermission existing = new UserPermission();
            existing.setId("existing-uuid");
            existing.setUserId("user-1");
            existing.setAction("READ");
            existing.setResource("USER");
            existing.setGrantedAt(LocalDateTime.now().minusDays(1));
            when(userPermissionMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(existing);

            LocalDateTime future = LocalDateTime.now().plusDays(7);
            UserPermission result = permissionService.assignPermission(
                    "user-1", "READ", "USER", future);

            assertThat(result.getId()).isEqualTo("existing-uuid");
            assertThat(result.getExpiresAt()).isEqualTo(future);
            verify(userPermissionMapper, times(1)).updateById(any(UserPermission.class));
            verify(userPermissionMapper, never()).insert(any(UserPermission.class));
        }

        @Test
        @DisplayName("rejects past expiresAt with VALIDATION_FAILED")
        void rejectsPastExpiresAt() {
            LocalDateTime past = LocalDateTime.now().minusSeconds(10);

            assertThatThrownBy(() -> permissionService.assignPermission(
                    "user-1", "READ", "USER", past))
                    .isInstanceOf(AuthBusinessException.class)
                    .satisfies(ex -> assertThat(((AuthBusinessException) ex).getErrorCode())
                            .isEqualTo(BaseErrorCode.VALIDATION_FAILED));

            verify(userPermissionMapper, never()).insert(any(UserPermission.class));
            verify(userPermissionMapper, never()).updateById(any(UserPermission.class));
        }
    }

    @Nested
    @DisplayName("validatePermissionArgs()")
    class ValidatePermissionArgs {

        @Test
        @DisplayName("rejects action not in ENUM whitelist with VALIDATION_FAILED")
        void rejectsUnknownAction() {
            assertThatThrownBy(() -> permissionService.assignPermission(
                    "user-1", "FOOBAR", "USER", null))
                    .isInstanceOf(AuthBusinessException.class)
                    .satisfies(ex -> assertThat(((AuthBusinessException) ex).getErrorCode())
                            .isEqualTo(BaseErrorCode.VALIDATION_FAILED));
        }

        @Test
        @DisplayName("rejects resource not in ENUM whitelist")
        void rejectsUnknownResource() {
            assertThatThrownBy(() -> permissionService.assignPermission(
                    "user-1", "READ", "NOT_A_RESOURCE", null))
                    .isInstanceOf(AuthBusinessException.class)
                    .satisfies(ex -> assertThat(((AuthBusinessException) ex).getErrorCode())
                            .isEqualTo(BaseErrorCode.VALIDATION_FAILED));
        }

        @Test
        @DisplayName("rejects '*' wildcard action")
        void rejectsWildcardAction() {
            assertThatThrownBy(() -> permissionService.assignPermission(
                    "user-1", "*", "USER", null))
                    .isInstanceOf(AuthBusinessException.class);
        }

        @Test
        @DisplayName("rejects '*' wildcard resource")
        void rejectsWildcardResource() {
            assertThatThrownBy(() -> permissionService.assignPermission(
                    "user-1", "READ", "*", null))
                    .isInstanceOf(AuthBusinessException.class);
        }

        @Test
        @DisplayName("rejects blank userId/action/resource")
        void rejectsBlankArgs() {
            assertThatThrownBy(() -> permissionService.assignPermission(
                    "", "READ", "USER", null))
                    .isInstanceOf(AuthBusinessException.class);
            assertThatThrownBy(() -> permissionService.assignPermission(
                    "user-1", "  ", "USER", null))
                    .isInstanceOf(AuthBusinessException.class);
            assertThatThrownBy(() -> permissionService.assignPermission(
                    "user-1", "READ", null, null))
                    .isInstanceOf(AuthBusinessException.class);
        }

        @Test
        @DisplayName("accepts all 8 actions and 9 resources")
        void acceptsAllWhitelistedValues() {
            String[] actions = {
                    "CREATE", "READ", "UPDATE", "DELETE",
                    "MODERATE", "PUBLISH", "MANAGE_USERS", "MANAGE_PERMISSIONS"
            };
            String[] resources = {
                    "USER", "PROBLEM", "CONTEST", "SOLUTION",
                    "FORUM_POST", "FORUM_COMMENT", "SYSTEM", "PROBLEM_LIST", "TAG"
            };
            for (String act : actions) {
                for (String res : resources) {
                    boolean result = permissionService.revokePermission("user-1", act, res);
                    assertThat(result).isFalse();
                }
            }
        }
    }

    @Nested
    @DisplayName("revokePermission()")
    class RevokePermission {

        @Test
        @DisplayName("returns true when row exists")
        void returnsTrue() {
            when(userPermissionMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);

            boolean result = permissionService.revokePermission("user-1", "READ", "USER");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("returns false when row does not exist")
        void returnsFalseNoOp() {
            when(userPermissionMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);

            boolean result = permissionService.revokePermission("user-1", "READ", "USER");

            assertThat(result).isFalse();
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
        @DisplayName("includes only user permissions when role is null")
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

        @Test
        @DisplayName("filters out user_permissions with past expires_at")
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
