package com.ulticode.modules.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.permission.entity.RolePermission;
import com.ulticode.modules.permission.entity.UserPermission;
import com.ulticode.modules.permission.mapper.RolePermissionMapper;
import com.ulticode.modules.permission.mapper.UserPermissionMapper;
import com.ulticode.modules.permission.service.PermissionService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 权限服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final UserPermissionMapper userPermissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserMapper userMapper;
    private final Clock clock;
    private final UuidGenerator uuidGenerator;

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

    @Override
    public List<UserPermission> getUserPermissions(String userId) {
        return userPermissionMapper.selectList(
            new LambdaQueryWrapper<UserPermission>()
                .eq(UserPermission::getUserId, userId)
        );
    }

    @Override
    public List<String> getUserPermissionStrings(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return List.of();
        }

        String role = user.getRole();
        Set<String> permissions = new HashSet<>();

        if (role != null) {
            List<RolePermission> rolePerms = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>()
                    .eq(RolePermission::getRole, role)
            );
            for (RolePermission p : rolePerms) {
                permissions.add(p.getAction() + ":" + p.getResource());
            }
        }

        List<UserPermission> userPerms = getUserPermissions(userId);
        for (UserPermission p : userPerms) {
            permissions.add(p.getAction() + ":" + p.getResource());
        }

        return new ArrayList<>(permissions);
    }

    @Override
    public boolean hasPermission(String userId, String action, String resource) {
        List<String> permissions = getUserPermissionStrings(userId);

        return permissions.stream().anyMatch(p -> {
            String[] parts = p.split(":");
            if (parts.length != 2) return false;
            String permAction = parts[0];
            String permResource = parts[1];

            return (permAction.equals("*") || permAction.equals(action)) &&
                   (permResource.equals("*") || permResource.equals(resource));
        });
    }

    @Override
    public UserPermission assignPermission(String userId, String action, String resource,
                                            LocalDateTime expiresAt) {
        validatePermissionArgs(userId, action, resource);
        if (expiresAt != null && !expiresAt.isAfter(LocalDateTime.now(clock))) {
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
        record.setGrantedAt(LocalDateTime.now(clock));
        record.setGrantedBy(currentAdminId());
        record.setExpiresAt(expiresAt);

        if (existing == null) {
            record.setId(uuidGenerator.newId());
            userPermissionMapper.insert(record);
            log.info("Permission granted (new): user={} {}:{} expiresAt={}",
                userId, action, resource, expiresAt);
        } else {
            record.setId(existing.getId());
            userPermissionMapper.updateById(record);
            log.info("Permission re-granted (updated): user={} {}:{} expiresAt={}",
                userId, action, resource, expiresAt);
        }
        return record;
    }

    @Override
    public boolean revokePermission(String userId, String action, String resource) {
        validatePermissionArgs(userId, action, resource);

        int rows = userPermissionMapper.delete(
            new LambdaQueryWrapper<UserPermission>()
                .eq(UserPermission::getUserId, userId)
                .eq(UserPermission::getAction, action)
                .eq(UserPermission::getResource, resource)
        );
        if (rows > 0) {
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

    private String currentAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && StringUtils.hasText(auth.getName())) {
            return auth.getName();
        }
        return SYSTEM_GRANTOR;
    }
}
