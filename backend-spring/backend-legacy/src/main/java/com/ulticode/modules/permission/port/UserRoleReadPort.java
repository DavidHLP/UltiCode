package com.ulticode.modules.permission.port;

import java.util.Optional;

/**
 * Read-only role lookup over the user domain.
 *
 * <p>Narrows the user surface consumed by cross-cutting modules (permission) to
 * a single role-existence query, so consumers no longer import
 * {@code user.entity.User} or {@code user.mapper.UserMapper}. The result
 * distinguishes <em>user does not exist</em> (empty) from <em>user exists with
 * no role</em> (present, {@link UserRole#role()} {@code == null}) so callers can
 * branch on existence without a second lookup or an entity leak.
 *
 * @author ulticode
 */
public interface UserRoleReadPort {

    /**
     * Resolve the role for a user.
     *
     * @param userId the user id
     * @return the role view, or empty if the user does not exist; never null
     */
    Optional<UserRole> findRole(String userId);

    /** Lightweight role view; {@code role} is null when the user exists but has none assigned. */
    record UserRole(String role) {}
}
