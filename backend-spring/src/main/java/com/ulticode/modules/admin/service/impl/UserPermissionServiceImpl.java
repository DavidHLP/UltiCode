package com.ulticode.modules.admin.service.impl;

import com.ulticode.common.annotation.Audited;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.util.AuditContext;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.modules.admin.projection.AdminUserProjection;
import com.ulticode.modules.admin.service.UserPermissionService;
import com.ulticode.modules.permission.entity.UserPermission;
import com.ulticode.modules.permission.service.PermissionService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户权限授予服务实现：在角色权限之外，为用户授予 / 撤销直接权限。
 *
 * <p>从原 {@code AdminUserServiceImpl}（611 行）拆分而来（架构评审 Candidate 1）。
 * 用户档案 CRUD 与封禁逻辑移至 {@link UserManagementServiceImpl}。
 *
 * <p>关键安全守卫：
 * <ul>
 *   <li>HIGH-1：{@code MANAGE_PERMISSIONS:SYSTEM} 限制为 SUPER_ADMIN，
 *       防止普通 ADMIN 通过授权他人权限间接放大自己的权限。</li>
 *   <li>所有授予 / 撤销操作均通过 {@link PermissionService} 完成，
 *       由其负责底层幂等、过期与 Redis 失效。</li>
 * </ul>
 *
 * <p>与 {@link AdminUserProjection} 的协作：
 * 授权 / 撤销完成后调用 {@link AdminUserProjection#getUserById(String)}
 * 返回含最新 permissions 列表的 {@link AdminUserVO}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPermissionServiceImpl implements UserPermissionService {

    private final UserMapper userMapper;
    private final PermissionService permissionService;
    private final AdminUserProjection adminUserProjection;
    private final Clock clock;

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.GRANT_PERMISSION,
             entityType = AuditVocabulary.ENTITY_PERMISSION,
             userIdFrom = "id")
    public AdminUserVO assignUserPermission(String id, String action, String resource,
                                             LocalDateTime expiresAt) {
        // HIGH-1 守卫：授予 MANAGE_PERMISSIONS:SYSTEM 必须 SUPER_ADMIN
        requireSuperAdminForManagePermissionsSystem(action, resource);

        return performPermissionChange(id, action, resource, expiresAt, false);
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.REVOKE_PERMISSION,
             entityType = AuditVocabulary.ENTITY_PERMISSION,
             userIdFrom = "id")
    public AdminUserVO revokeUserPermission(String id, String action, String resource) {
        // 撤销 MANAGE_PERMISSIONS:SYSTEM 同样限制为 SUPER_ADMIN，
        // 防止 ADMIN 撤销他人 SUPER_ADMIN 权限导致锁死。
        requireSuperAdminForManagePermissionsSystem(action, resource);

        return performPermissionChange(id, action, resource, null, true);
    }

    /**
     * assign / revoke 公共逻辑：用户存在性校验 + before 快照 + AuditContext +
     * 委托底层 PermissionService + 返回最新 VO。{@code isRevoke} 决定调哪个底层方法
     * 以及 newValues 中写 removed 还是 grantedAt。
     */
    private AdminUserVO performPermissionChange(String id, String action, String resource,
                                                 LocalDateTime expiresAt, boolean isRevoke) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 抓 before 状态供审计（expiresAt 可为 null，Map.of 禁用，改用 HashMap）
        UserPermission before = permissionService.getUserPermissions(id).stream()
            .filter(p -> action.equals(p.getAction()) && resource.equals(p.getResource()))
            .findFirst()
            .orElse(null);

        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("action", action);
        oldValues.put("resource", resource);
        oldValues.put("expiresAt", before != null && before.getExpiresAt() != null
            ? before.getExpiresAt() : "");
        oldValues.put("grantedAt", before != null && before.getGrantedAt() != null
            ? before.getGrantedAt() : "");
        AuditContext.setOldValues(oldValues);

        boolean removed;
        if (isRevoke) {
            removed = permissionService.revokePermission(id, action, resource);
        } else {
            permissionService.assignPermission(id, action, resource, expiresAt);
            removed = false;
        }

        Map<String, Object> newValues = new HashMap<>();
        newValues.put("action", action);
        newValues.put("resource", resource);
        if (isRevoke) {
            newValues.put("removed", removed);
        } else {
            newValues.put("expiresAt", expiresAt != null ? expiresAt : "");
            newValues.put("grantedAt", LocalDateTime.now(clock));
        }
        AuditContext.setNewValues(newValues);

        if (isRevoke && !removed) {
            log.info("Revoke no-op (permission not present): user={} {}:{}",
                id, action, resource);
        } else if (!isRevoke) {
            log.info("Permission assigned: user={} {}:{} expiresAt={}",
                id, action, resource, expiresAt);
        }
        return adminUserProjection.getUserById(id);
    }

    /**
     * HIGH-1：MANAGE_PERMISSIONS:SYSTEM 是「管理他人权限」能力，属于特权操作，
     * 与 deleteUser / bulkDelete 一致仅 SUPER_ADMIN 可执行。
     * 当前 actor 不是 SUPER_ADMIN 时直接抛 FORBIDDEN。
     */
    private void requireSuperAdminForManagePermissionsSystem(String action, String resource) {
        boolean isManagePermissionsSystem =
            "MANAGE_PERMISSIONS".equals(action) && "SYSTEM".equals(resource);
        if (!isManagePermissionsSystem) {
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isSuperAdmin = auth != null
            && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_SUPER_ADMIN"::equals);
        if (!isSuperAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                "Granting/revoking MANAGE_PERMISSIONS:SYSTEM requires SUPER_ADMIN role");
        }
    }
}
