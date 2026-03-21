package com.ulticode.modules.achievement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.achievement.entity.UserAchievement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Mapper for UserAchievement entity.
 */
@Mapper
public interface UserAchievementMapper extends BaseMapper<UserAchievement> {

    /**
     * Find all user achievements for a user.
     *
     * @param userId the user ID
     * @return list of user achievements
     */
    @Select("SELECT * FROM user_achievements WHERE user_id = #{userId}")
    List<UserAchievement> findByUserId(@Param("userId") String userId);

    /**
     * Find a specific user achievement.
     *
     * @param userId the user ID
     * @param achievementId the achievement ID
     * @return the user achievement or null
     */
    @Select("SELECT * FROM user_achievements WHERE user_id = #{userId} AND achievement_id = #{achievementId}")
    UserAchievement findByUserAndAchievement(
            @Param("userId") String userId,
            @Param("achievementId") String achievementId);

    /**
     * Count achievements earned by a user.
     *
     * @param userId the user ID
     * @return count of achievements earned
     */
    @Select("SELECT COUNT(*) FROM user_achievements WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") String userId);
}
