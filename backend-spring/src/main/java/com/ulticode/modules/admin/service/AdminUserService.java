package com.ulticode.modules.admin.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminCreateUserDTO;
import com.ulticode.modules.admin.dto.AdminUpdateUserDTO;
import com.ulticode.modules.admin.dto.AdminUserQueryDTO;
import com.ulticode.modules.admin.dto.AdminUserVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for admin user management operations
 */
public interface AdminUserService {

    /**
     * Get paginated list of users with filters
     */
    PageResult<AdminUserVO> getUsers(AdminUserQueryDTO query);

    /**
     * Get user by ID
     */
    AdminUserVO getUserById(String id);

    /**
     * Create a new user
     */
    AdminUserVO createUser(AdminCreateUserDTO dto);

    /**
     * Update user information
     */
    AdminUserVO updateUser(String id, AdminUpdateUserDTO dto);

    /**
     * Delete a user
     */
    void deleteUser(String id);

    /**
     * Ban a user
     */
    AdminUserVO banUser(String id, String reason, String until);

    /**
     * Unban a user
     */
    AdminUserVO unbanUser(String id);

    /**
     * Reset user password
     */
    void resetPassword(String id, String newPassword);

    /**
     * Bulk ban users
     */
    List<BanResult> bulkBan(List<String> ids, String reason);

    /**
     * Bulk unban users
     */
    List<BanResult> bulkUnban(List<String> ids);

    /**
     * Bulk delete users
     */
    List<DeleteResult> bulkDelete(List<String> ids);

    /**
     * 授予用户一条直接权限 (user_permissions 表),与 role 权限互补合并。
     * 幂等:已存在则更新 expiresAt;不存在则插入。
     *
     * @param id 被授权用户 ID
     * @param action user_permissions.action 的 ENUM 值
     * @param resource user_permissions.resource 的 ENUM 值
     * @param expiresAt 过期时间,null 表示永久;非 null 必须严格晚于当前时间
     * @return 含最新 permissions 列表的 AdminUserVO
     */
    AdminUserVO assignUserPermission(String id, String action, String resource,
                                      LocalDateTime expiresAt);

    /**
     * 撤销用户一条直接权限。不存在该权限时正常返回 (符合 REST DELETE 幂等语义)。
     *
     * @return 含最新 permissions 列表的 AdminUserVO
     */
    AdminUserVO revokeUserPermission(String id, String action, String resource);

    /**
     * Result of bulk ban operation
     */
    record BanResult(String id, boolean success, String error) {}

    /**
     * Result of bulk delete operation
     */
    record DeleteResult(String id, boolean success, String error) {}
}
