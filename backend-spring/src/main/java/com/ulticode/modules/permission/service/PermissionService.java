package com.ulticode.modules.permission.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.permission.entity.UserPermission;
import com.ulticode.modules.permission.mapper.UserPermissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 权限服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final UserPermissionMapper userPermissionMapper;
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
     */
    public List<String> getUserPermissionStrings(String userId) {
        List<UserPermission> permissions = getUserPermissions(userId);
        return permissions.stream()
            .map(p -> p.getAction() + ":" + p.getResource())
            .collect(Collectors.toList());
    }

    /**
     * 检查用户是否有特定权限
     */
    public boolean hasPermission(String userId, String action, String resource) {
        List<UserPermission> permissions = getUserPermissions(userId);
        return permissions.stream()
            .anyMatch(p ->
                (p.getAction().equals("*") || p.getAction().equals(action)) &&
                (p.getResource().equals("*") || p.getResource().equals(resource))
            );
    }

    /**
     * 清除用户权限缓存
     */
    public void invalidateCache(String userId) {
        redisTemplate.delete(PERM_CACHE_PREFIX + userId);
    }
}
