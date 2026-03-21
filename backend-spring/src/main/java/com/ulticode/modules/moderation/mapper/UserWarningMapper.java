package com.ulticode.modules.moderation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.moderation.entity.UserWarning;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Mapper interface for UserWarning entity.
 */
@Mapper
public interface UserWarningMapper extends BaseMapper<UserWarning> {

    /**
     * Find all warnings for a specific user.
     *
     * @param userId the user ID
     * @return list of warnings
     */
    @Select("SELECT * FROM user_warnings WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<UserWarning> findByUserId(@Param("userId") String userId);

    /**
     * Count active warnings for a user (not expired).
     *
     * @param userId the user ID
     * @return count of active warnings
     */
    @Select("SELECT COUNT(*) FROM user_warnings WHERE user_id = #{userId} AND (expires_at IS NULL OR expires_at > NOW())")
    long countActiveWarnings(@Param("userId") String userId);

    /**
     * Count all warnings for a user.
     *
     * @param userId the user ID
     * @return count of all warnings
     */
    @Select("SELECT COUNT(*) FROM user_warnings WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") String userId);
}
