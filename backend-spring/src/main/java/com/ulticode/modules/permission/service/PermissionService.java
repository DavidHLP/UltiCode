package com.ulticode.modules.permission.service;

import com.ulticode.modules.permission.entity.UserPermission;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 权限服务接口 — check, assign, revoke, cache invalidation.
 *
 * <p>Split from the former concrete {@code PermissionService} class (248 LOC,
 * no interface) per the project's Java convention that all {@code @Service}
 * classes must expose an interface with an {@code Impl} suffix on the
 * implementation (see {@code backend/01-java-programming.md} §(四)16).
 *
 * <p>Architecture review candidate #6 — extract interface from fused
 * concrete service. Callers depend on this interface, not on
 * {@code PermissionServiceImpl}. The deletion test passes: deleting the
 * interface forces all callers to import the impl, widening the coupling
 * surface.
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
     * 清除用户权限缓存
     */
    void invalidateCache(String userId);

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
