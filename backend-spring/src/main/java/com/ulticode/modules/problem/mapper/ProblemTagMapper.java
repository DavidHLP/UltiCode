package com.ulticode.modules.problem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.problem.entity.ProblemTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper for problem_tags table.
 */
@Mapper
public interface ProblemTagMapper extends BaseMapper<ProblemTag> {
}
