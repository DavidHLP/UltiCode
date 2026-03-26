package com.ulticode.modules.solution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.solution.entity.SolutionComment;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis Mapper for SolutionComment entity.
 */
@Mapper
public interface SolutionCommentMapper extends BaseMapper<SolutionComment> {
    // Basic CRUD operations inherited from BaseMapper
}
