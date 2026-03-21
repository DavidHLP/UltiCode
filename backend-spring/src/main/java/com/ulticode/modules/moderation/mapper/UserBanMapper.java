package com.ulticode.modules.moderation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.moderation.entity.UserBan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * Mapper interface for UserBan entity.
 */
@Mapper
public interface UserBanMapper extends BaseMapper<UserBan> {

    /**
     * Find all bans for a specific user.
     *
     * @param userId the user ID
     * @return list of bans
     */
    @Select("SELECT * FROM user_bans WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<UserBan> findByUserId(@Param("userId") String userId);

    /**
     * Find the active ban for a user.
     *
     * @param userId the user ID
     * @return the active ban or empty
     */
    @Select("SELECT * FROM user_bans WHERE user_id = #{userId} AND (is_permanent = 1 OR expires_at > NOW()) ORDER BY created_at DESC LIMIT 1")
    Optional<UserBan> findActiveBan(@Param("userId") String userId);

    /**
     * Check if a user has an active ban.
     *
     * @param userId the user ID
     * @return true if the user has an active ban
     */
    @Select("SELECT COUNT(*) > 0 FROM user_bans WHERE user_id = #{userId} AND (is_permanent = 1 OR expires_at > NOW())")
    boolean hasActiveBan(@Param("userId") String userId);

    /**
     * Count all bans for a user.
     *
     * @param userId the user ID
     * @return count of all bans
     */
    @Select("SELECT COUNT(*) FROM user_bans WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") String userId);
}
