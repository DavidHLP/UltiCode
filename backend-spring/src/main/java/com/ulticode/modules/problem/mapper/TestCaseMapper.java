package com.ulticode.modules.problem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.problem.entity.TestCase;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Mapper for test_cases table.
 */
@Mapper
public interface TestCaseMapper extends BaseMapper<TestCase> {

    /**
     * Select all test cases for a problem, ordered by test_order.
     */
    default List<TestCase> findByProblemIdOrderByOrder(Long problemId) {
        return selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TestCase>()
                .eq(TestCase::getProblemId, problemId)
                .orderByAsc(TestCase::getTestOrder)
        );
    }

    /**
     * Select sample test cases only.
     */
    default List<TestCase> findSampleByProblemId(Long problemId) {
        return selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TestCase>()
                .eq(TestCase::getProblemId, problemId)
                .eq(TestCase::getIsSample, true)
                .orderByAsc(TestCase::getTestOrder)
        );
    }
}
