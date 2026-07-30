package com.ulticode.auth.account.mapper;

import com.ulticode.auth.account.entity.AuthAccountEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** Auth-owned account persistence mapper. */
@Mapper
public interface AuthAccountMapper {
    String COLUMNS = "id, username, email, password, role, is_active AS active, "
            + "is_banned AS banned, banned_until, joined_at, authz_version, "
            + "password_reset_token_hash, password_reset_expires_at";

    @Select("SELECT " + COLUMNS + " FROM users WHERE username = #{username} AND is_deleted = 0 LIMIT 1")
    AuthAccountEntity findByUsername(String username);

    @Select("SELECT " + COLUMNS + " FROM users WHERE email = #{email} AND is_deleted = 0 LIMIT 1")
    AuthAccountEntity findByEmail(String email);

    @Select("SELECT " + COLUMNS + " FROM users WHERE id = #{id} AND is_deleted = 0")
    AuthAccountEntity findById(String id);

    @Select("<script>SELECT " + COLUMNS + " FROM users WHERE is_deleted = 0 AND id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<AuthAccountEntity> findByIds(@Param("ids") Set<String> ids);

    @Insert("INSERT INTO users (id, username, email, password, role, is_active, is_banned, "
            + "banned_until, joined_at, authz_version) VALUES (#{id}, #{username}, #{email}, "
            + "#{password}, #{role}, #{active}, #{banned}, #{bannedUntil}, #{joinedAt}, #{authzVersion})")
    int insert(AuthAccountEntity account);

    @Update("UPDATE users SET last_login_at = CURRENT_TIMESTAMP(3) WHERE id = #{id} AND is_deleted = 0")
    int updateLastLoginAt(String id);

    @Update("UPDATE users SET password = #{password} WHERE id = #{id} AND is_deleted = 0")
    int updatePassword(@Param("id") String id, @Param("password") String password);

    /**
     * Unified atomic CAS update for account state and role.
     * Guarantees a single SQL statement increment of authz_version.
     */
    @Update("UPDATE users SET is_active = #{active}, is_banned = #{banned}, role = #{role}, "
            + "authz_version = authz_version + 1 WHERE id = #{id} AND is_deleted = 0 "
            + "AND authz_version = #{expectedVersion}")
    int updateAccountIfVersion(@Param("id") String id, @Param("active") boolean active,
                               @Param("banned") boolean banned, @Param("role") String role,
                               @Param("expectedVersion") long expectedVersion);

    @Update("UPDATE users SET password_reset_token_hash = #{tokenHash}, password_reset_expires_at = #{expiresAt} "
            + "WHERE id = #{id} AND is_deleted = 0")
    int savePasswordReset(@Param("id") String id, @Param("tokenHash") String tokenHash,
                          @Param("expiresAt") LocalDateTime expiresAt);

    @Update("UPDATE users SET password_reset_token_hash = NULL, password_reset_expires_at = NULL "
            + "WHERE id = #{id} AND is_deleted = 0")
    int clearPasswordReset(String id);

    @Select("SELECT " + COLUMNS + " FROM users WHERE is_deleted = 0 AND password_reset_token_hash IS NOT NULL "
            + "AND password_reset_expires_at > #{now}")
    List<AuthAccountEntity> findUsersWithActivePasswordReset(LocalDateTime now);
}
