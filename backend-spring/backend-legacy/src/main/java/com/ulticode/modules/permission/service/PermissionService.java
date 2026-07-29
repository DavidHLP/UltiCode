package com.ulticode.modules.permission.service;

import com.ulticode.modules.permission.entity.UserPermission;

import java.util.List;

/**
 * 权限服务接口 — read-only permission queries.
 *
 * <p><strong>P2-DISC-006:</strong> assignPermission and revokePermission write methods
 * are dropped from this interface. Production write calls route to backend-auth
 * via BackendAuthRoleAdminClient.
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
}
