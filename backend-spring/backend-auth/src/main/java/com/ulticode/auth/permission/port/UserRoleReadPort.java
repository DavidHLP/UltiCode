package com.ulticode.auth.permission.port;

import java.util.Optional;

/**
 * Read-only user role lookup seam for permission calculations.
 */
public interface UserRoleReadPort {

    /**
     * Resolve the role for a user.
     *
     * @param userId the user id
     * @return the role view, or empty if user does not exist
     */
    Optional<UserRole> findRole(String userId);

    /** Lightweight role view. */
    record UserRole(String role) {}
}
