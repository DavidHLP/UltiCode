package com.ulticode.auth.permission.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Mapper for the {@code users.role} column. P2-RBAC-001 owner-only
 * write path: backend-auth is the single writer; App / Admin / legacy
 * modules must call the HTTP command surface (see
 * {@link com.ulticode.auth.adapter.in.web.RoleAdministrationController})
 * rather than touching this mapper directly. The
 * {@code com.ulticode.auth..} package is the only one allowed to
 * depend on this mapper; the ArchUnit foreign-writer rule enforces
 * the boundary.
 *
 * <p>Deliberately minimal — only the UPDATE path is exposed. Reads
 * of {@code users.role} for JWT / permission evaluation continue to
 * go through {@code AuthAccountPort#findById} (read-only).
 */
@Mapper
public interface UserRoleMapper {

    /**
     * Update a single user's role. Idempotent: if the new role equals
     * the current role, neither the row nor {@code authz_version} is bumped;
     * a real role change increments {@code authz_version} atomically.
     *
     * @return 1 if the row existed and the role changed; 0 if the row
     *     did not exist (caller should treat as
     *     {@code AUTH_USER_NOT_FOUND}); the no-op same-role case is
     *     also reported as 0
     */
    @Update("UPDATE users SET role = #{newRole}, authz_version = authz_version + 1 "
            + "WHERE id = #{userId} AND role <> #{newRole}")
    int updateRole(@Param("userId") String userId, @Param("newRole") String newRole);

    /**
     * Lightweight existence check for users. Used by the
     * role-administration service to translate a 0-row UPDATE into
     * an {@code AUTH_USER_NOT_FOUND} rather than a silent no-op.
     */
    @Select("SELECT 1 FROM users WHERE id = #{userId}")
    Integer existsById(@Param("userId") String userId);
}
