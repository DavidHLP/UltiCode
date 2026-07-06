package com.ulticode.modules.permission.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.permission.entity.UserPermission;
import com.ulticode.modules.permission.mapper.RolePermissionMapper;
import com.ulticode.modules.permission.mapper.UserPermissionMapper;
import com.ulticode.modules.permission.service.impl.PermissionServiceImpl;
import com.ulticode.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
// 明示导入以消除 BaseMapper 重载歧义
import static org.mockito.ArgumentMatchers.anyCollection;

/**
 * MEDIUM-4:覆盖 PermissionService 的安全敏感路径 —
 * 通配符拒绝、过期时间拒绝、缓存失效、insert/update 路径选择、revoke 幂等。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PermissionService")
class PermissionServiceTest {

    @Mock
    private UserPermissionMapper userPermissionMapper;

    @Mock
    private RolePermissionMapper rolePermissionMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    private PermissionService permissionService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        permissionService = new PermissionServiceImpl(
            userPermissionMapper, rolePermissionMapper, userMapper, redisTemplate);
    }

    @Nested
    @DisplayName("assignPermission()")
    class AssignPermission {

        @Test
        @DisplayName("inserts new row when no existing record, and invalidates cache")
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
            verify(userPermissionMapper, times(1)).insert(org.mockito.ArgumentMatchers.<UserPermission>any());
            verify(userPermissionMapper, never()).updateById(any(UserPermission.class));
            verify(redisTemplate, times(1)).delete("user:perms:user-1");
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

            // 复用原 ID 而非生成新的
            assertThat(result.getId()).isEqualTo("existing-uuid");
            assertThat(result.getExpiresAt()).isEqualTo(future);
            verify(userPermissionMapper, times(1)).updateById(any(UserPermission.class));
            verify(userPermissionMapper, never()).insert(org.mockito.ArgumentMatchers.<UserPermission>any());
            verify(redisTemplate, times(1)).delete("user:perms:user-1");
        }

        @Test
        @DisplayName("rejects past expiresAt with VALIDATION_FAILED")
        void rejectsPastExpiresAt() {
            LocalDateTime past = LocalDateTime.now().minusSeconds(1);

            assertThatThrownBy(() -> permissionService.assignPermission(
                "user-1", "READ", "USER", past))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_FAILED));

            verify(userPermissionMapper, never()).insert(org.mockito.ArgumentMatchers.<UserPermission>any());
            verify(userPermissionMapper, never()).updateById(any(UserPermission.class));
            verify(redisTemplate, never()).delete((String) any());
        }
    }

    @Nested
    @DisplayName("validatePermissionArgs() — via public API")
    class ValidatePermissionArgs {

        @Test
        @DisplayName("HIGH-2: rejects action not in ENUM whitelist with VALIDATION_FAILED")
        void rejectsUnknownAction() {
            assertThatThrownBy(() -> permissionService.assignPermission(
                "user-1", "FOOBAR", "USER", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_FAILED));
        }

        @Test
        @DisplayName("HIGH-2: rejects resource not in ENUM whitelist")
        void rejectsUnknownResource() {
            assertThatThrownBy(() -> permissionService.assignPermission(
                "user-1", "READ", "NOT_A_RESOURCE", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_FAILED));
        }

        @Test
        @DisplayName("rejects '*' wildcard action")
        void rejectsWildcardAction() {
            assertThatThrownBy(() -> permissionService.assignPermission(
                "user-1", "*", "USER", null))
                .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("rejects '*' wildcard resource")
        void rejectsWildcardResource() {
            assertThatThrownBy(() -> permissionService.assignPermission(
                "user-1", "READ", "*", null))
                .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("rejects blank userId/action/resource")
        void rejectsBlankArgs() {
            assertThatThrownBy(() -> permissionService.assignPermission(
                "", "READ", "USER", null))
                .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> permissionService.assignPermission(
                "user-1", "  ", "USER", null))
                .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> permissionService.assignPermission(
                "user-1", "READ", null, null))
                .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("accepts all 8 actions and 9 resources (whitelist coverage)")
        void acceptsAllWhitelistedValues() {
            String[] actions = {
                "CREATE", "READ", "UPDATE", "DELETE",
                "MODERATE", "PUBLISH", "MANAGE_USERS", "MANAGE_PERMISSIONS"
            };
            String[] resources = {
                "USER", "PROBLEM", "CONTEST", "SOLUTION",
                "FORUM_POST", "FORUM_COMMENT", "SYSTEM", "PROBLEM_LIST", "TAG"
            };
            // 只测校验阶段 —— mock 抛 NPE 也无所谓,只要不在 validatePermissionArgs 抛 BusinessException
            for (String act : actions) {
                for (String res : resources) {
                    // 用 revoke(无 mapper 交互即可让 validatePermissionArgs 先执行)
                    boolean result;
                    try {
                        result = permissionService.revokePermission("user-1", act, res);
                    } catch (BusinessException e) {
                        throw new AssertionError(
                            "Whitelisted (" + act + ":" + res + ") was rejected", e);
                    }
                    // revoke 路径默认返回 false(未匹配行)且不动 cache
                    assertThat(result).isFalse();
                }
            }
        }
    }

    @Nested
    @DisplayName("revokePermission()")
    class RevokePermission {

        @Test
        @DisplayName("returns true and invalidates cache when row exists")
        void returnsTrueAndInvalidates() {
            when(userPermissionMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);

            boolean result = permissionService.revokePermission("user-1", "READ", "USER");

            assertThat(result).isTrue();
            verify(redisTemplate, times(1)).delete("user:perms:user-1");
        }

        @Test
        @DisplayName("returns false (no-op) when row does not exist, no cache touch")
        void returnsFalseNoOp() {
            when(userPermissionMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);

            boolean result = permissionService.revokePermission("user-1", "READ", "USER");

            assertThat(result).isFalse();
            verify(redisTemplate, never()).delete((String) any());
        }
    }
}
