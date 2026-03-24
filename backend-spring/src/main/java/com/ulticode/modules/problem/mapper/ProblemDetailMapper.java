package com.ulticode.modules.problem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.problem.entity.ProblemDetail;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper for problem_details table.
 */
@Mapper
public interface ProblemDetailMapper extends BaseMapper<ProblemDetail> {
}
