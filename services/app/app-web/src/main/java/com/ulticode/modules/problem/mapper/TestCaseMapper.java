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

    /**
     * Select cases eligible to drive a formal verdict (P0-1).
     *
     * <p>A case is "judging-eligible" iff its {@code is_sample} and {@code is_hidden}
     * flags are an <b>exclusive or</b>: exactly one is true.
     * <ul>
     *   <li>{@code is_sample=true, is_hidden=false} — public sample (also used for verdict)</li>
     *   <li>{@code is_sample=false, is_hidden=true} — private judge case</li>
     * </ul>
     * Both other combinations ({@code true,true} illegal; {@code false,false} draft)
     * are <b>excluded</b>. The {@code @TableLogic isDeleted} column is auto-filtered
     * by MyBatis-Plus; no explicit {@code is_deleted=0} clause is needed.
     *
     * <p>An empty result means the problem has no judging-eligible cases. The
     * execution worker must fail closed with a System Error verdict, not
     * silently fall back to {@code problem_examples}.
     */
    default List<TestCase> findActiveCasesForJudging(Long problemId) {
        return selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TestCase>()
                .eq(TestCase::getProblemId, problemId)
                .and(w -> w
                    .and(s -> s.eq(TestCase::getIsSample, true).eq(TestCase::getIsHidden, false))
                    .or(s -> s.eq(TestCase::getIsSample, false).eq(TestCase::getIsHidden, true))
                )
                .orderByAsc(TestCase::getTestOrder)
        );
    }
}
