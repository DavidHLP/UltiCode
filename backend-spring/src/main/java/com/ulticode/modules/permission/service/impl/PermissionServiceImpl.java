package com.ulticode.modules.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.permission.PermissionVocabulary;
import com.ulticode.modules.permission.entity.RolePermission;
import com.ulticode.modules.permission.entity.UserPermission;
import com.ulticode.modules.permission.mapper.RolePermissionMapper;
import com.ulticode.modules.permission.mapper.UserPermissionMapper;
import com.ulticode.modules.permission.service.PermissionService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 权限服务实现。
 *
 * <p>Allowed-action / allowed-resource whitelists live in
 * {@link PermissionVocabulary} — this class only consults them via the
 * vocabulary's {@code isAllowedAction} / {@code isAllowedResource}
 * predicates. Adding or dropping an ENUM value is a one-file change.
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
    private final PermissionVocabulary vocabulary;
    private final CurrentUserProvider currentUserProvider;

    private static final String SYSTEM_GRANTOR = "system";

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
        if (!vocabulary.isAllowedAction(action)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                "Unsupported action: " + action
                    + " (allowed: " + vocabulary.allowedActions() + ")");
        }
        if (!vocabulary.isAllowedResource(resource)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                "Unsupported resource: " + resource
                    + " (allowed: " + vocabulary.allowedResources() + ")");
        }
    }

    private String currentAdminId() {
        // Reads through the CurrentUserProvider port. SecurityContextHolder
        // is the sole touchpoint of SecurityCurrentUserProvider (the only
        // adapter), so this is the only place in the file that needs the
        // security context.
        String id = currentUserProvider.getCurrentUserId();
        return StringUtils.hasText(id) ? id : SYSTEM_GRANTOR;
    }
}
