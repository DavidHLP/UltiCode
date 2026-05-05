package com.ulticode.modules.solution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.solution.entity.SolutionComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * MyBatis Mapper for SolutionComment entity.
 */
@Mapper
public interface SolutionCommentMapper extends BaseMapper<SolutionComment> {

    @Select("SELECT COUNT(*) FROM solution_comments WHERE solution_id = #{solutionId} AND is_deleted = 0")
    long countBySolutionId(@Param("solutionId") String solutionId);

    @Update("UPDATE solution_comments SET is_flagged = #{isFlagged}, flagged_reason = #{reason}, flagged_at = CASE WHEN #{isFlagged} = true THEN NOW() ELSE NULL END WHERE id = #{id}")
    int updateFlagStatus(@Param("id") String id, @Param("isFlagged") boolean isFlagged, @Param("reason") String reason);
}
