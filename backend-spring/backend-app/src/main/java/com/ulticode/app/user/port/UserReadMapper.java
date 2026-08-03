package com.ulticode.app.user.port;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Q-read mapper over the Auth-owned {@code users} table for the
 * relocated user-surface projections
 * (P7-RELOCATE-USER-REMAINDER-001).
 *
 * <p>Read-only: no INSERT/UPDATE/DELETE. Same Q-classified shared-read
 * precedent as {@code UserSearchReadMapper} and
 * {@code GlobalRankingMapper}. Profile mutations go through
 * {@code UserProfileMapper} (App-owned write path); account mutations
 * (password, role) stay in Auth.
 */
@Mapper
public interface UserReadMapper {

    String COLUMNS = """
            id, username, name, email, avatar, bio, company, github,
            joined_at, location, twitter, website, preferred_language,
            role, is_active, is_banned, last_login_at""";

    @Select("SELECT " + COLUMNS + " FROM users WHERE id = #{id} AND is_deleted = 0")
    UserSummaryView selectById(@Param("id") String id);

    @Select("SELECT " + COLUMNS + " FROM users WHERE username = #{username} AND is_deleted = 0")
    UserSummaryView selectByUsername(@Param("username") String username);

    @Select("SELECT " + COLUMNS + " FROM users WHERE is_active = 1 AND is_banned = 0 "
            + "AND is_deleted = 0 ORDER BY joined_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<UserSummaryView> selectActiveUsers(@Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM users WHERE is_active = 1 AND is_banned = 0 AND is_deleted = 0")
    long countActiveUsers();

    @Select("SELECT COUNT(*) FROM users WHERE id = #{id} AND is_deleted = 0")
    int countById(@Param("id") String id);
}
