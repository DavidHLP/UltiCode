package com.ulticode.app.user.port;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * Q-write mapper for profile columns on the Auth-owned {@code users}
 * table (P7-RELOCATE-USER-REMAINDER-001).
 *
 * <p>During the dual-write window (P5-USERPROFILE-001), profile
 * mutations must also update the {@code users} table so
 * {@link UserReadMapper} (which Q-reads from {@code users}) returns
 * consistent data. This mapper writes only profile columns — it never
 * touches account columns (password, role, is_active, is_banned).
 *
 * <p>This is a transitional Q-class seam: once the dual-write window
 * ends and profile columns are removed from {@code users}, this mapper
 * and its callers are deleted, and {@link UserReadMapper} switches to
 * reading from {@code user_profiles}.
 */
@Mapper
public interface UserProfileWriteMapper {

    @Update("UPDATE users SET name = #{name} WHERE id = #{userId} AND is_deleted = 0")
    int updateName(@Param("userId") String userId, @Param("name") String name);

    @Update("UPDATE users SET avatar = #{avatar} WHERE id = #{userId} AND is_deleted = 0")
    int updateAvatar(@Param("userId") String userId, @Param("avatar") String avatar);

    @Update("UPDATE users SET bio = #{bio} WHERE id = #{userId} AND is_deleted = 0")
    int updateBio(@Param("userId") String userId, @Param("bio") String bio);

    @Update("UPDATE users SET company = #{company} WHERE id = #{userId} AND is_deleted = 0")
    int updateCompany(@Param("userId") String userId, @Param("company") String company);

    @Update("UPDATE users SET github = #{github} WHERE id = #{userId} AND is_deleted = 0")
    int updateGithub(@Param("userId") String userId, @Param("github") String github);

    @Update("UPDATE users SET location = #{location} WHERE id = #{userId} AND is_deleted = 0")
    int updateLocation(@Param("userId") String userId, @Param("location") String location);

    @Update("UPDATE users SET twitter = #{twitter} WHERE id = #{userId} AND is_deleted = 0")
    int updateTwitter(@Param("userId") String userId, @Param("twitter") String twitter);

    @Update("UPDATE users SET website = #{website} WHERE id = #{userId} AND is_deleted = 0")
    int updateWebsite(@Param("userId") String userId, @Param("website") String website);

    @Update("UPDATE users SET preferred_language = #{preferredLanguage} WHERE id = #{userId} AND is_deleted = 0")
    int updatePreferredLanguage(@Param("userId") String userId, @Param("preferredLanguage") String preferredLanguage);
}
