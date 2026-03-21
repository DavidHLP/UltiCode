package com.ulticode.modules.achievement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.achievement.entity.Achievement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Mapper for Achievement entity.
 */
@Mapper
public interface AchievementMapper extends BaseMapper<Achievement> {

    /**
     * Find achievement by key.
     *
     * @param key the achievement key
     * @return the achievement or null
     */
    @Select("SELECT * FROM achievements WHERE key = #{key}")
    Achievement findByKey(@Param("key") String key);

    /**
     * Find all active achievements.
     *
     * @return list of active achievements
     */
    @Select("SELECT * FROM achievements WHERE is_active = 1 ORDER BY category ASC, tier ASC")
    List<Achievement> findAllActive();
}
