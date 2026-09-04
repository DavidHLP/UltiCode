package com.ulticode.auth.permission.service;

import com.ulticode.auth.permission.entity.UserPermission;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Permission service interface — read, assign, revoke.
 */
public interface PermissionService {

    /**
     * Get all direct user permissions for a single user.
     */
    List<UserPermission> getUserPermissions(String userId);

    /**
     * Bulk fetch direct user permissions for multiple users without N+1 queries.
     */
    Map<String, List<UserPermission>> getBatchUserPermissions(Set<String> userIds);

    /**
     * Get combined permission strings (format: ACTION:RESOURCE).
     */
    List<String> getUserPermissionStrings(String userId);

    /**
     * Compatibility helper for in-process callers. It resolves the current
     * authenticated actor and fails closed when no actor is present.
     */
    UserPermission assignPermission(String userId, String action, String resource,
                                    LocalDateTime expiresAt);

    /**
     * Assign a direct permission using the authenticated actor supplied by
     * the Auth-owned mutation command.
     */
    UserPermission assignPermission(String userId, String action, String resource,
                                    LocalDateTime expiresAt, String grantedBy);

    /** Revoke a direct permission from a user. */
    boolean revokePermission(String userId, String action, String resource);
}
