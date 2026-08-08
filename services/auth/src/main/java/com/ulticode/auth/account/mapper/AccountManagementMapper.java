package com.ulticode.auth.account.mapper;

import com.ulticode.auth.account.entity.AuthAccountEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** MyBatis statements for auth-owned account-management mutations. */
@Mapper
public interface AccountManagementMapper {

    String COLUMNS = "id, username, email, password, role, is_active AS active, "
            + "is_banned AS banned, banned_until, joined_at, authz_version, "
            + "password_reset_token_hash, password_reset_expires_at";

    @Select("SELECT " + COLUMNS
            + " FROM users WHERE id = #{accountId} AND is_deleted = 0 LIMIT 1")
    AuthAccountEntity findById(@Param("accountId") String accountId);

    @Select("SELECT " + COLUMNS
            + " FROM users WHERE username = #{username} AND is_deleted = 0 LIMIT 1")
    AuthAccountEntity findByUsername(@Param("username") String username);

    @Select("SELECT " + COLUMNS
            + " FROM users WHERE email = #{email} AND is_deleted = 0 LIMIT 1")
    AuthAccountEntity findByEmail(@Param("email") String email);

    @Insert("INSERT INTO users (id, username, email, password, role, is_active, is_banned, "
            + "banned_until, joined_at, authz_version) VALUES (#{id}, #{username}, #{email}, "
            + "#{password}, #{role}, #{active}, #{banned}, #{bannedUntil}, #{joinedAt}, "
            + "#{authzVersion})")
    int insert(AuthAccountEntity account);

    @Update("UPDATE users SET username = #{username}, email = #{email}, "
            + "updated_by = #{updatedBy} WHERE id = #{accountId} AND is_deleted = 0")
    int updateCredentials(@Param("accountId") String accountId,
                          @Param("username") String username,
                          @Param("email") String email,
                          @Param("updatedBy") String updatedBy);

    @Update("UPDATE users SET password = #{hashedPassword}, updated_by = #{updatedBy} "
            + "WHERE id = #{accountId} AND is_deleted = 0")
    int updatePassword(@Param("accountId") String accountId,
                       @Param("hashedPassword") String hashedPassword,
                       @Param("updatedBy") String updatedBy);

    @Update("UPDATE users SET is_deleted = 1, deleted_at = CURRENT_TIMESTAMP(3), "
            + "deleted_by = #{deletedBy}, updated_by = #{deletedBy} "
            + "WHERE id = #{accountId} AND is_deleted = 0")
    int softDelete(@Param("accountId") String accountId,
                   @Param("deletedBy") String deletedBy);
}
