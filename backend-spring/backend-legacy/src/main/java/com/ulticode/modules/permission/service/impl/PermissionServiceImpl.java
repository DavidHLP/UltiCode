package com.ulticode.modules.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.permission.entity.RolePermission;
import com.ulticode.modules.permission.entity.UserPermission;
import com.ulticode.modules.permission.mapper.RolePermissionMapper;
import com.ulticode.modules.permission.mapper.UserPermissionMapper;
import com.ulticode.modules.permission.port.UserRoleReadPort;
import com.ulticode.modules.permission.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 权限服务实现。
 *
 * <p>User role lookups go through {@link UserRoleReadPort} — a consumer-owned
 * seam declared in this module ({@code permission.port}) and backed by
 * {@code user.port.UserRoleReadAdapter}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final UserPermissionMapper userPermissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleReadPort userRoleReadPort;
    private final Clock clock;

    @Override
    public List<UserPermission> getUserPermissions(String userId) {
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
}
