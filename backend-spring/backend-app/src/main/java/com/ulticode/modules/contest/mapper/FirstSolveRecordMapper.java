package com.ulticode.modules.contest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.contest.entity.FirstSolveRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

/**
 * MyBatis-Plus mapper for FirstSolveRecord entity.
 *
 * <p>The unique key (contest_id, problem_id) is what makes first-solve detection
 * race-safe. Use {@link #insertAndReturnAffected} to atomically detect whether the
 * caller is the first solver.
 */
@Mapper
public interface FirstSolveRecordMapper extends BaseMapper<FirstSolveRecord> {

    @Select("SELECT * FROM first_solve_records WHERE contest_id = #{contestId} "
            + "AND problem_id = #{problemId} LIMIT 1")
    Optional<FirstSolveRecord> findByContestIdAndProblemId(
            @Param("contestId") String contestId,
            @Param("problemId") Long problemId
    );

    @Delete("DELETE FROM first_solve_records WHERE contest_id = #{contestId}")
    int deleteByContestId(@Param("contestId") String contestId);
}
