package com.ulticode.modules.permission.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.permission.entity.RolePermission;
import com.ulticode.modules.permission.entity.UserPermission;
import com.ulticode.modules.permission.mapper.RolePermissionMapper;
import com.ulticode.modules.permission.mapper.UserPermissionMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 权限服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final UserPermissionMapper userPermissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String PERM_CACHE_PREFIX = "user:perms:";
    private static final String SYSTEM_GRANTOR = "system";

    /**
     * user_permissions.action ENUM 的合法取值(与 DDL 一致)。
     * 防止 MySQL 在收到非法值时抛 DataIntegrityViolationException 泄露 SQL 错误。
     */
    private static final Set<String> ALLOWED_ACTIONS = Set.of(
        "CREATE", "READ", "UPDATE", "DELETE",
        "MODERATE", "PUBLISH", "MANAGE_USERS", "MANAGE_PERMISSIONS");

    /**
     * user_permissions.resource ENUM 的合法取值(与 DDL 一致)。
     */
    private static final Set<String> ALLOWED_RESOURCES = Set.of(
        "USER", "PROBLEM", "CONTEST", "SOLUTION",
        "FORUM_POST", "FORUM_COMMENT", "SYSTEM", "PROBLEM_LIST", "TAG");

    /**
     * 获取用户所有权限
     */
    public List<UserPermission> getUserPermissions(String userId) {
        return userPermissionMapper.selectList(
            new LambdaQueryWrapper<UserPermission>()
                .eq(UserPermission::getUserId, userId)
        );
    }

    /**
     * 获取用户权限列表（格式：action:resource）
     * 合并角色权限和用户特定权限
     */
    public List<String> getUserPermissionStrings(String userId) {
        // 1. 获取用户信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            return List.of();
        }

        String role = user.getRole();
        Set<String> permissions = new HashSet<>();

        // 2. 获取角色权限
        if (role != null) {
            List<RolePermission> rolePerms = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>()
                    .eq(RolePermission::getRole, role)
            );
            for (RolePermission p : rolePerms) {
                permissions.add(p.getAction() + ":" + p.getResource());
            }
        }

        // 3. 获取用户特定权限
        List<UserPermission> userPerms = getUserPermissions(userId);
        for (UserPermission p : userPerms) {
            permissions.add(p.getAction() + ":" + p.getResource());
        }

        return new ArrayList<>(permissions);
    }

    /**
     * 检查用户是否有特定权限
     */
    public boolean hasPermission(String userId, String action, String resource) {
        List<String> permissions = getUserPermissionStrings(userId);
        String requiredPerm = action + ":" + resource;

        return permissions.stream().anyMatch(p -> {
            String[] parts = p.split(":");
            if (parts.length != 2) return false;
            String permAction = parts[0];
            String permResource = parts[1];

            return (permAction.equals("*") || permAction.equals(action)) &&
                   (permResource.equals("*") || permResource.equals(resource));
        });
    }

    /**
     * 清除用户权限缓存
     */
    public void invalidateCache(String userId) {
        redisTemplate.delete(PERM_CACHE_PREFIX + userId);
    }

    /**
     * 授予用户一条直接权限(幂等)。
     *
     * <p>语义:
     * <ul>
     *   <li>(userId, action, resource) 已存在 → 更新 expiresAt 并重置 grantedAt/grantedBy</li>
     *   <li>(userId, action, resource) 不存在 → 插入新行</li>
     *   <li>expiresAt 必须严格晚于当前时间,null 表示永久授权</li>
     * </ul>
     *
     * <p>成功路径会触发 {@link #invalidateCache(String)}; 校验失败抛
     * {@link BusinessException} 不清缓存。
     *
     * @param userId 被授权用户 ID
     * @param action 操作 (必须匹配 user_permissions.action 的 ENUM 值)
     * @param resource 资源 (必须匹配 user_permissions.resource 的 ENUM 值)
     * @param expiresAt 过期时间, null 表示永久
     * @return 实际写入/更新的 UserPermission 记录
     * @throws BusinessException 当 userId/action/resource 为空/为 "*",
     *         或 expiresAt 已过期时
     */
    public UserPermission assignPermission(String userId, String action, String resource,
                                            LocalDateTime expiresAt) {
        validatePermissionArgs(userId, action, resource);
        if (expiresAt != null && !expiresAt.isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                "expiresAt must be in the future");
        }

        UserPermission existing = userPermissionMapper.selectOne(
            new LambdaQueryWrapper<UserPermission>()
                .eq(UserPermission::getUserId, userId)
                .eq(UserPermission::getAction, action)
                .eq(UserPermission::getResource, resource)
        );

        UserPermission record = new UserPermission();
        record.setUserId(userId);
        record.setAction(action);
        record.setResource(resource);
        record.setGrantedAt(LocalDateTime.now());
        record.setGrantedBy(currentAdminId());
        record.setExpiresAt(expiresAt);

        if (existing == null) {
            record.setId(UUID.randomUUID().toString());
            userPermissionMapper.insert(record);
            log.info("Permission granted (new): user={} {}:{} expiresAt={}",
                userId, action, resource, expiresAt);
        } else {
            record.setId(existing.getId());
            userPermissionMapper.updateById(record);
            log.info("Permission re-granted (updated): user={} {}:{} expiresAt={}",
                userId, action, resource, expiresAt);
        }
        invalidateCache(userId);
        return record;
    }

    /**
     * 撤销用户一条直接权限。
     *
     * <p>不存在该权限时返回 false 不抛异常 (符合 REST DELETE 幂等语义)。
     * 成功删除后触发 {@link #invalidateCache(String)}。
     *
     * @return true 表示存在并删除; false 表示原本不存在
     */
    public boolean revokePermission(String userId, String action, String resource) {
        validatePermissionArgs(userId, action, resource);

        int rows = userPermissionMapper.delete(
            new LambdaQueryWrapper<UserPermission>()
                .eq(UserPermission::getUserId, userId)
                .eq(UserPermission::getAction, action)
                .eq(UserPermission::getResource, resource)
        );
        if (rows > 0) {
            invalidateCache(userId);
            log.info("Permission revoked: user={} {}:{}", userId, action, resource);
            return true;
        }
        log.debug("Permission revoke no-op (not present): user={} {}:{}",
            userId, action, resource);
        return false;
    }

    private void validatePermissionArgs(String userId, String action, String resource) {
        if (!StringUtils.hasText(userId)
                || !StringUtils.hasText(action)
                || !StringUtils.hasText(resource)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                "userId/action/resource must not be blank");
        }
        if ("*".equals(action) || "*".equals(resource)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                "Wildcard '*' grant/revoke is not allowed via this endpoint");
        }
        if (!ALLOWED_ACTIONS.contains(action)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                "Unsupported action: " + action
                    + " (allowed: " + ALLOWED_ACTIONS + ")");
        }
        if (!ALLOWED_RESOURCES.contains(resource)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                "Unsupported resource: " + resource
                    + " (allowed: " + ALLOWED_RESOURCES + ")");
        }
    }

    /**
     * 取当前认证主体的 username,无认证时降级为 SYSTEM_GRANTOR("system")。
     * 用于 user_permissions.granted_by 列。注意:此列存的是 username 而非 user ID
     * (与项目 SecurityUtil / SystemSettingsServiceImpl 等审计列约定一致)。
     */
    private String currentAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && StringUtils.hasText(auth.getName())) {
            return auth.getName();
        }
        return SYSTEM_GRANTOR;
    }
}
