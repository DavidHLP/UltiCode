package com.ulticode.modules.admin.service;

import com.ulticode.modules.admin.dto.AdminUserVO;

import java.time.LocalDateTime;

/**
 * 用户权限授予服务：在角色权限之外，为用户授予 / 撤销直接权限（{@code user_permissions} 表）。
 *
 * <p>从原 {@code AdminUserService} 拆分而来（架构评审 Candidate 1）。
 * 用户档案 CRUD 与封禁逻辑移至 {@link UserManagementService}。
 *
 * <p>该接口仅承担「直接权限」语义，与角色权限（{@code role_permissions}）解耦：
 * <ul>
 *   <li>{@link #assignUserPermission} 幂等插入 / 更新直接权限；</li>
 *   <li>{@link #revokeUserPermission} 符合 REST DELETE 幂等语义，权限不存在时仍返回成功；</li>
 *   <li>{@code MANAGE_PERMISSIONS:SYSTEM} 限制为 SUPER_ADMIN，防止普通 ADMIN
 *       通过授权他人权限间接放大自己的权限（HIGH-1 安全守卫）。</li>
 * </ul>
 */
public interface UserPermissionService {

    /**
     * 授予用户一条直接权限。
     *
     * <p>幂等：已存在则更新 {@code expiresAt}；不存在则插入。
     *
     * @param id         被授权用户 ID
     * @param action     {@code user_permissions.action} 的 ENUM 值
     * @param resource   {@code user_permissions.resource} 的 ENUM 值
     * @param expiresAt  过期时间，{@code null} 表示永久；非 {@code null} 必须严格晚于当前时间
     * @return 含最新 permissions 列表的 {@link AdminUserVO}
     */
    AdminUserVO assignUserPermission(String id, String action, String resource,
                                      LocalDateTime expiresAt);

    /**
     * 撤销用户一条直接权限。
     *
     * <p>不存在该权限时正常返回（符合 REST DELETE 幂等语义）。
     *
     * @return 含最新 permissions 列表的 {@link AdminUserVO}
     */
    AdminUserVO revokeUserPermission(String id, String action, String resource);
}
