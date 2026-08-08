package com.ulticode.app.user.port;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Q-read mapper joining the Auth-owned {@code users} table (account
 * columns) with the App-owned {@code user_profiles} table (profile
 * columns) for the user-surface projections.
 *
 * <p>Read-only: no INSERT/UPDATE/DELETE. Account columns (username,
 * email, role, is_active, is_banned, last_login_at, joined_at) come
 * from {@code users}; profile columns (name, avatar, bio, company,
 * github, location, twitter, website, preferred_language) come from
 * {@code user_profiles} via LEFT JOIN.
 */
@Mapper
public interface UserReadMapper {

    String COLUMNS = """
            u.id, u.username, p.name, u.email, p.avatar, p.bio, p.company, p.github,
            u.joined_at, p.location, p.twitter, p.website, p.preferred_language,
            u.role, u.is_active, u.is_banned, u.last_login_at""";

    String JOIN = " FROM users u LEFT JOIN user_profiles p ON u.id = p.account_id";

    @Select("SELECT " + COLUMNS + JOIN + " WHERE u.id = #{id} AND u.is_deleted = 0")
    UserSummaryView selectById(@Param("id") String id);

    @Select("SELECT " + COLUMNS + JOIN + " WHERE u.username = #{username} AND u.is_deleted = 0")
    UserSummaryView selectByUsername(@Param("username") String username);

    @Select("SELECT " + COLUMNS + JOIN + " WHERE u.is_active = 1 AND u.is_banned = 0 "
            + "AND u.is_deleted = 0 ORDER BY u.joined_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<UserSummaryView> selectActiveUsers(@Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM users WHERE is_active = 1 AND is_banned = 0 AND is_deleted = 0")
    long countActiveUsers();

    @Select("SELECT COUNT(*) FROM users WHERE id = #{id} AND is_deleted = 0")
    int countById(@Param("id") String id);
}
