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
import com.ulticode.modules.permission.port.UserRoleReadPort;
import com.ulticode.modules.permission.service.PermissionService;
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
 *
 * <p>User role lookups go through {@link UserRoleReadPort} — a consumer-owned
 * seam declared in this module ({@code permission.port}) and backed by
 * {@code user.port.UserRoleReadAdapter}. This class no longer imports
 * {@code user.entity.User} or {@code user.mapper.UserMapper}.
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
        // Phase 0 / MICROSERVICE_MIGRATION_GUIDE.md §7.1: exclude rows whose
        // expires_at is in the past. Null expires_at = permanent grant;
        // future expires_at = still valid; past expires_at = stale and
        // must not be exposed via /auth/permissions. Backed by the
        // user_permissions.expires_at column added in
        // V20260610140000__Add_User_Permission_Expires_At.sql.
        return userPermissionMapper.selectList(
            new LambdaQueryWrapper<UserPermission>()
                .eq(UserPermission::getUserId, userId)
                .and(w -> w.isNull(UserPermission::getExpiresAt)
                        .or().gt(UserPermission::getExpiresAt, LocalDateTime.now(clock)))
        );
    }

    @Override
    public List<String> getUserPermissionStrings(String userId) {
        return userRoleReadPort.findRole(userId)
            .map(roleView -> {
                Set<String> permissions = new HashSet<>();
                String role = roleView.role();
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
                List<String> merged = new ArrayList<>(permissions);
                return merged;
            })
            .orElse(List.of());
    }

    @Override
    public UserPermission assignPermission(String userId, String action, String resource,
                                            LocalDateTime expiresAt) {
        // P2-RBAC-001: backend-auth is the sole owner of the
        // user_permissions write path. The legacy's read-side
        // (getUserPermissionStrings etc.) stays; the write side
        // was routed through the BackendAuthRoleAdminClient in
        // UserPermissionServiceImpl. This method remains for
        // binary compatibility (PermissionService interface) but
        // throws a directive error if anything still calls it.
        // P2-DISC-006 removes it entirely once the old
        // PermissionServiceTest cases are deleted.
        throw new UnsupportedOperationException(
            "P2-RBAC-001: legacy PermissionService.assignPermission is closed. "
                + "Use BackendAuthRoleAdminClient.grantPermission "
                + "(proxied to backend-auth /auth/admin/users/{id}/permissions).");
    }

    @Override
    public boolean revokePermission(String userId, String action, String resource) {
        // P2-RBAC-001: see assignPermission above. The legacy
        // revoke path is closed; production callers must use
        // BackendAuthRoleAdminClient.revokePermission (proxied to
        // backend-auth /auth/admin/users/{id}/permissions). The
        // method is kept only for binary compatibility with the
        // PermissionService interface; P2-DISC-006 removes it.
        throw new UnsupportedOperationException(
            "P2-RBAC-001: legacy PermissionService.revokePermission is closed. "
                + "Use BackendAuthRoleAdminClient.revokePermission "
                + "(proxied to backend-auth /auth/admin/users/{id}/permissions).");
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
