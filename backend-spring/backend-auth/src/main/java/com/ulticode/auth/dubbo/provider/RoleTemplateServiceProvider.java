package com.ulticode.auth.dubbo.provider;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.auth.api.dto.PermissionEntry;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.RoleTemplateService;
import com.ulticode.auth.permission.entity.RolePermission;
import com.ulticode.auth.permission.mapper.RolePermissionMapper;
import com.ulticode.common.rpc.RpcResult;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dubbo provider for {@link RoleTemplateService}.
 *
 * <p>Reads the {@code role_permissions} table directly and maps each
 * row to a {@link PermissionEntry} with {@code source = "role"} and
 * {@code expiresAt = null} (role-template permissions never expire).
 *
 * <p>Role existence is validated against the canonical role set
 * defined by the {@code users.role} / {@code role_permissions.role}
 * DDL enum constraint ({@code USER, MODERATOR, ADMIN, SUPER_ADMIN}).
 * An unknown role name returns {@link AuthErrorCode#ROLE_NOT_FOUND};
 * a known role with zero template rows returns an empty success list.
 */
@Component
@DubboService(group = "backend-auth", version = "1.0.0")
public class RoleTemplateServiceProvider implements RoleTemplateService {

    private static final Set<String> VALID_ROLES = Set.of(
            "USER", "MODERATOR", "ADMIN", "SUPER_ADMIN");

    private final RolePermissionMapper rolePermissionMapper;

    public RoleTemplateServiceProvider(RolePermissionMapper rolePermissionMapper) {
        this.rolePermissionMapper = rolePermissionMapper;
    }

    @Override
    public RpcResult<List<PermissionEntry>> getRoleTemplate(String role) {
        if (role == null || role.isBlank()) {
            return RpcResult.failure(AuthErrorCode.ROLE_NOT_FOUND, "t-system");
        }
        String normalized = role.trim().toUpperCase();
        if (!VALID_ROLES.contains(normalized)) {
            return RpcResult.failure(AuthErrorCode.ROLE_NOT_FOUND, "t-system");
        }

        List<RolePermission> rolePerms = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>()
                        .eq(RolePermission::getRole, normalized));

        List<PermissionEntry> entries = rolePerms.stream()
                .map(rp -> new PermissionEntry(
                        rp.getAction(),
                        rp.getResource(),
                        "role",
                        null))
                .collect(Collectors.toCollection(ArrayList::new));

        return RpcResult.success(entries, "t-system");
    }
}
