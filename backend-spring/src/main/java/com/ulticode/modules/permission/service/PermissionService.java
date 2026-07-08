package com.ulticode.modules.permission.service;

import com.ulticode.modules.permission.entity.UserPermission;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 权限服务接口 — check, assign, revoke.
 *
 * <p>Split from the former concrete {@code PermissionService} class (248 LOC,
 * no interface) per the project's Java convention that all {@code @Service}
 * classes must expose an interface with an {@code Impl} suffix on the
 * implementation (see {@code backend/01-java-programming.md} §(四)16).
 *
 * <p><strong>Cache seam removed (architecture review 2026-07-08).</strong>
 * The previous interface carried an {@code invalidateCache(userId)} method
 * backed by a write-only {@code RedisTemplate} dependency — the cache was
 * invalidated on every write but never read on any read path
 * ({@code getUserPermissionStrings} recomputed from the database on every
 * call). The deletion test forced the cleanup: removing the method removes
 * the only consumer of the cache key, which removes the Redis dependency,
 * which removes a whole category of stale-cache bugs the stub cache would
 * have caused if a future caller had added a cache read. The service is
 * now pure-DB; a real cache is a future PR that adds a read path with a
 * proper consistency story.
 *
 * @author ulticode
 */
public interface PermissionService {

    /**
     * 获取用户所有权限
     */
    List<UserPermission> getUserPermissions(String userId);

    /**
     * 获取用户权限列表（格式：action:resource）
     * 合并角色权限和用户特定权限
     */
    List<String> getUserPermissionStrings(String userId);

    /**
     * 检查用户是否有特定权限
     */
    boolean hasPermission(String userId, String action, String resource);

    /**
     * 授予用户一条直接权限(幂等)。
     *
     * @param userId 被授权用户 ID
     * @param action 操作 (必须匹配 user_permissions.action 的 ENUM 值)
     * @param resource 资源 (必须匹配 user_permissions.resource 的 ENUM 值)
     * @param expiresAt 过期时间, null 表示永久
     * @return 实际写入/更新的 UserPermission 记录
     */
    UserPermission assignPermission(String userId, String action, String resource,
                                     LocalDateTime expiresAt);

    /**
     * 撤销用户一条直接权限。
     *
     * @return true 表示存在并删除; false 表示原本不存在
     */
    boolean revokePermission(String userId, String action, String resource);
}
