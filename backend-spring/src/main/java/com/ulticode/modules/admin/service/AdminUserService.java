package com.ulticode.modules.admin.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminCreateUserDTO;
import com.ulticode.modules.admin.dto.AdminUpdateUserDTO;
import com.ulticode.modules.admin.dto.AdminUserQueryDTO;
import com.ulticode.modules.admin.dto.AdminUserVO;

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
     * Result of bulk ban operation
     */
    record BanResult(String id, boolean success, String error) {}

    /**
     * Result of bulk delete operation
     */
    record DeleteResult(String id, boolean success, String error) {}
}
