package com.ulticode.modules.problem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.problem.entity.ProblemExample;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Mapper for problem_examples table.
 */
@Mapper
public interface ProblemExampleMapper extends BaseMapper<ProblemExample> {

    /**
     * Select all examples for a problem, ordered by example_order.
     */
    default List<ProblemExample> findByProblemIdOrderByOrder(Long problemId) {
        return selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProblemExample>()
                .eq(ProblemExample::getProblemId, problemId)
                .orderByAsc(ProblemExample::getExampleOrder)
        );
    }
}
