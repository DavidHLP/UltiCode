package com.ulticode.auth.reconciliation;

import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Reconciliation-only read model over the Auth-owned {@code users}
 * table.
 *
 * <p>Deliberately separate from {@code AuthAccountMapper}: that mapper
 * filters {@code is_deleted = 0} everywhere, while reconciliation needs
 * (a) the non-deleted count for the dual-write pair and (b) physical
 * existence including soft-deleted rows — the legacy orphan rule
 * "orphan = parent id does not exist at all".
 */
@Mapper
public interface ReconciliationQueryMapper {

    @Select("SELECT COUNT(*) FROM users WHERE is_deleted = 0")
    long countActiveUsers();

    /**
     * Physical existence check: rows are matched regardless of the
     * is_deleted flag.
     */
    @Select("<script>"
            + "SELECT id FROM users WHERE id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>"
            + "#{id}"
            + "</foreach>"
            + "</script>")
    Set<String> selectExistingIds(@Param("ids") Set<String> ids);

    @Select("SELECT COUNT(*) FROM refresh_tokens c LEFT JOIN users p ON c.user_id = p.id "
            + "WHERE c.user_id IS NOT NULL AND p.id IS NULL")
    long countOrphanRefreshTokens();

    @Select("SELECT COUNT(*) FROM password_resets c LEFT JOIN users p ON c.user_id = p.id "
            + "WHERE c.user_id IS NOT NULL AND p.id IS NULL")
    long countOrphanPasswordResets();

    @Select("SELECT COUNT(*) FROM oauth_provider_identities c LEFT JOIN users p ON c.user_id = p.id "
            + "WHERE c.user_id IS NOT NULL AND p.id IS NULL")
    long countOrphanOauthProviderIdentities();

    @Select("SELECT COUNT(*) FROM user_permissions c LEFT JOIN users p ON c.user_id = p.id "
            + "WHERE c.user_id IS NOT NULL AND p.id IS NULL")
    long countOrphanUserPermissions();
}
