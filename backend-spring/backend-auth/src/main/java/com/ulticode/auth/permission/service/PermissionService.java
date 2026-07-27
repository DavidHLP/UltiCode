package com.ulticode.auth.permission.service;

import com.ulticode.auth.permission.entity.UserPermission;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Permission service interface — read, assign, revoke.
 */
public interface PermissionService {

    /**
     * Get all direct user permissions.
     */
    List<UserPermission> getUserPermissions(String userId);

    /**
     * Get combined permission strings (format: ACTION:RESOURCE).
     */
    List<String> getUserPermissionStrings(String userId);

    /**
     * Assign a direct permission to a user (idempotent).
     */
    UserPermission assignPermission(String userId, String action, String resource,
                                    LocalDateTime expiresAt);

    /**
     * Revoke a direct permission from a user.
     */
    boolean revokePermission(String userId, String action, String resource);
}
