package com.ulticode.auth.account.mapper;

import com.ulticode.auth.account.entity.AuthAccountEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** MyBatis read mapper for Auth account queries. */
@Mapper
public interface AuthAccountQueryMapper {

    String COLUMNS = "id, username, email, password, role, is_active AS active, "
            + "is_banned AS banned, banned_reason AS bannedReason, banned_until, "
            + "joined_at, last_login_at, authz_version, "
            + "password_reset_token_hash, password_reset_expires_at, "
            + "updated_at AS updatedAt, deleted_at AS deletedAt";

    @Select("SELECT " + COLUMNS + " FROM users WHERE id = #{accountId} AND is_deleted = 0 LIMIT 1")
    AuthAccountEntity findById(@Param("accountId") String accountId);

    @Select("SELECT " + COLUMNS + " FROM users WHERE username = #{username} AND is_deleted = 0 LIMIT 1")
    AuthAccountEntity findByUsername(@Param("username") String username);

    @Select("SELECT " + COLUMNS + " FROM users WHERE email = #{email} AND is_deleted = 0 LIMIT 1")
    AuthAccountEntity findByEmail(@Param("email") String email);

    @Select("<script>"
            + "SELECT " + COLUMNS + " FROM users WHERE is_deleted = 0 AND id IN "
            + "<foreach collection='accountIds' item='accountId' open='(' separator=',' close=')'>"
            + "#{accountId}</foreach> ORDER BY id ASC"
            + "</script>")
    List<AuthAccountEntity> findByIds(@Param("accountIds") List<String> accountIds);

    @Select("<script>"
            + "SELECT " + COLUMNS + " FROM users WHERE is_deleted = 0"
            + "<if test='search != null and search != \"\" and usernameOnly'>"
            + "  AND username LIKE CONCAT('%', #{search}, '%')"
            + "</if>"
            + "<if test='search != null and search != \"\" and !usernameOnly'>"
            + "  AND (username LIKE CONCAT('%', #{search}, '%') OR email LIKE CONCAT('%', #{search}, '%'))"
            + "</if>"
            + "<if test='role != null and role != \"\"'>"
            + "  AND role = #{role}"
            + "</if>"
            + "<if test='banned != null'>"
            + "  AND is_banned = #{banned}"
            + "</if>"
            + "<choose>"
            + "  <when test='sortBy == \"username\"'> ORDER BY username </when>"
            + "  <when test='sortBy == \"email\"'> ORDER BY email </when>"
            + "  <when test='sortBy == \"lastLoginAt\"'> ORDER BY last_login_at </when>"
            + "  <when test='sortBy == \"id\"'> ORDER BY id </when>"
            + "  <otherwise> ORDER BY joined_at </otherwise>"
            + "</choose>"
            + "<choose>"
            + "  <when test='sortOrder == \"asc\"'> ASC </when>"
            + "  <otherwise> DESC </otherwise>"
            + "</choose>"
            + " LIMIT #{offset}, #{limit}"
            + "</script>")
    List<AuthAccountEntity> queryAccounts(
            @Param("search") String search,
            @Param("role") String role,
            @Param("active") Boolean active,
            @Param("banned") Boolean banned,
            @Param("usernameOnly") boolean usernameOnly,
            @Param("sortBy") String sortBy,
            @Param("sortOrder") String sortOrder,
            @Param("offset") int offset,
            @Param("limit") int limit);

    @Select("<script>"
            + "SELECT COUNT(*) FROM users WHERE is_deleted = 0"
            + "<if test='search != null and search != \"\" and usernameOnly'>"
            + "  AND username LIKE CONCAT('%', #{search}, '%')"
            + "</if>"
            + "<if test='search != null and search != \"\" and !usernameOnly'>"
            + "  AND (username LIKE CONCAT('%', #{search}, '%') OR email LIKE CONCAT('%', #{search}, '%'))"
            + "</if>"
            + "<if test='role != null and role != \"\"'>"
            + "  AND role = #{role}"
            + "</if>"
            + "<if test='active != null'>"
            + "  AND is_active = #{active}"
            + "</if>"
            + "<if test='banned != null'>"
            + "  AND is_banned = #{banned}"
            + "</if>"
            + "</script>")
    long countAccounts(
            @Param("search") String search,
            @Param("role") String role,
            @Param("active") Boolean active,
            @Param("banned") Boolean banned,
            @Param("usernameOnly") boolean usernameOnly);
}
