package com.ulticode.modules.solution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.solution.entity.Solution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * MyBatis-Plus mapper for Solution entity.
 */
@Mapper
public interface SolutionMapper extends BaseMapper<Solution> {

    @Update("UPDATE solutions SET is_flagged = #{isFlagged}, flagged_reason = #{reason}, flagged_at = CASE WHEN #{isFlagged} = true THEN NOW() ELSE NULL END WHERE id = #{id}")
    int updateFlagStatus(@Param("id") String id, @Param("isFlagged") boolean isFlagged, @Param("reason") String reason);
}
