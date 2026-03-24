package com.ulticode.modules.permission.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.permission.entity.RolePermission;
import com.ulticode.modules.permission.entity.UserPermission;
import com.ulticode.modules.permission.mapper.RolePermissionMapper;
import com.ulticode.modules.permission.mapper.UserPermissionMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

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
}
