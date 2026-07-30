package com.ulticode.auth.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.permission.PermissionVocabulary;
import com.ulticode.auth.permission.entity.RolePermission;
import com.ulticode.auth.permission.entity.UserPermission;
import com.ulticode.auth.permission.mapper.RolePermissionMapper;
import com.ulticode.auth.permission.mapper.UserPermissionMapper;
import com.ulticode.auth.permission.port.UserRoleReadPort;
import com.ulticode.auth.permission.service.PermissionService;
import com.ulticode.auth.security.CurrentUserProvider;
import com.ulticode.auth.util.UuidGenerator;
import com.ulticode.common.error.BaseErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Permission service implementation inside backend-auth.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final UserPermissionMapper userPermissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleReadPort userRoleReadPort;
    private final Clock clock;
    private final UuidGenerator uuidGenerator;
    private final PermissionVocabulary vocabulary;
    private final CurrentUserProvider currentUserProvider;

    private static final String SYSTEM_GRANTOR = "system";

    @Override
    public List<UserPermission> getUserPermissions(String userId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return userPermissionMapper.selectList(
                new LambdaQueryWrapper<UserPermission>()
                        .eq(UserPermission::getUserId, userId.trim())
                        .and(w -> w.isNull(UserPermission::getExpiresAt)
                                .or()
                                .gt(UserPermission::getExpiresAt, now))
        );
    }

    @Override
    public Map<String, List<UserPermission>> getBatchUserPermissions(Set<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Set<String> clean = userIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toSet());
        if (clean.isEmpty()) {
            return Map.of();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        List<UserPermission> allPerms = userPermissionMapper.selectActivePermissionsByUserIds(clean, now);
        Map<String, List<UserPermission>> result = new HashMap<>();
        for (String id : clean) {
            result.put(id, new ArrayList<>());
        }
        for (UserPermission p : allPerms) {
            if (p.getUserId() != null && result.containsKey(p.getUserId())) {
                result.get(p.getUserId()).add(p);
            }
        }
        return result;
    }

    @Override
    public List<String> getUserPermissionStrings(String userId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }

        Optional<UserRoleReadPort.UserRole> roleOpt = userRoleReadPort.findRole(userId.trim());
        if (roleOpt.isEmpty()) {
            return List.of();
        }

        Set<String> permissions = new HashSet<>();

        // Role permissions
        String role = roleOpt.get().role();
        if (StringUtils.hasText(role)) {
            List<RolePermission> rolePerms = rolePermissionMapper.selectList(
                    new LambdaQueryWrapper<RolePermission>()
                            .eq(RolePermission::getRole, role.trim().toUpperCase())
            );
            for (RolePermission rp : rolePerms) {
                permissions.add(rp.getAction() + ":" + rp.getResource());
            }
        }

        // Direct user permissions
        List<UserPermission> userPerms = getUserPermissions(userId);
        for (UserPermission up : userPerms) {
            permissions.add(up.getAction() + ":" + up.getResource());
        }

        return new ArrayList<>(permissions);
    }

    @Override
    public UserPermission assignPermission(String userId, String action, String resource,
                                            LocalDateTime expiresAt) {
        validatePermissionArgs(userId, action, resource);

        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now(clock))) {
            throw new AuthBusinessException(BaseErrorCode.VALIDATION_FAILED, "expiresAt cannot be in the past");
        }

        String normUserId = userId.trim();
        String normAction = action.trim().toUpperCase();
        String normResource = resource.trim().toUpperCase();

        UserPermission existing = userPermissionMapper.selectOne(
                new LambdaQueryWrapper<UserPermission>()
                        .eq(UserPermission::getUserId, normUserId)
                        .eq(UserPermission::getAction, normAction)
                        .eq(UserPermission::getResource, normResource)
        );

        if (existing != null) {
            existing.setExpiresAt(expiresAt);
            userPermissionMapper.updateById(existing);
            return existing;
        }

        String actorId = currentUserProvider.getCurrentUserId();
        String grantedBy = StringUtils.hasText(actorId) ? actorId : SYSTEM_GRANTOR;

        UserPermission perm = new UserPermission();
        perm.setId(uuidGenerator.newId());
        perm.setUserId(normUserId);
        perm.setAction(normAction);
        perm.setResource(normResource);
        perm.setGrantedBy(grantedBy);
        perm.setGrantedAt(LocalDateTime.now(clock));
        perm.setExpiresAt(expiresAt);

        userPermissionMapper.insert(perm);
        return perm;
    }

    @Override
    public boolean revokePermission(String userId, String action, String resource) {
        validatePermissionArgs(userId, action, resource);

        int rows = userPermissionMapper.delete(
                new LambdaQueryWrapper<UserPermission>()
                        .eq(UserPermission::getUserId, userId.trim())
                        .eq(UserPermission::getAction, action.trim().toUpperCase())
                        .eq(UserPermission::getResource, resource.trim().toUpperCase())
        );

        return rows > 0;
    }

    private void validatePermissionArgs(String userId, String action, String resource) {
        if (!StringUtils.hasText(userId)) {
            throw new AuthBusinessException(BaseErrorCode.VALIDATION_FAILED, "userId cannot be blank");
        }
        if (!StringUtils.hasText(action)) {
            throw new AuthBusinessException(BaseErrorCode.VALIDATION_FAILED, "action cannot be blank");
        }
        if (!StringUtils.hasText(resource)) {
            throw new AuthBusinessException(BaseErrorCode.VALIDATION_FAILED, "resource cannot be blank");
        }

        if (!vocabulary.isAllowedAction(action.trim())) {
            throw new AuthBusinessException(BaseErrorCode.VALIDATION_FAILED, "Unknown permission action: " + action);
        }

        if (!vocabulary.isAllowedResource(resource.trim())) {
            throw new AuthBusinessException(BaseErrorCode.VALIDATION_FAILED, "Unknown permission resource: " + resource);
        }
    }
}
