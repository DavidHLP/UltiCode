package com.ulticode.modules.problem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.problem.entity.Problem;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus mapper for Problem entity.
 * Provides standard CRUD operations through BaseMapper.
 */
@Mapper
public interface ProblemMapper extends BaseMapper<Problem> {
}
