package com.ulticode.modules.contest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.contest.entity.ContestProblem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * MyBatis-Plus mapper for ContestProblem entity.
 */
@Mapper
public interface ContestProblemMapper extends BaseMapper<ContestProblem> {

    @Select("SELECT * FROM contest_problems WHERE contest_id = #{contestId} ORDER BY problem_index ASC")
    List<ContestProblem> findByContestId(@Param("contestId") String contestId);

    @Select("SELECT * FROM contest_problems WHERE contest_id = #{contestId} AND problem_id = #{problemId} LIMIT 1")
    ContestProblem findByContestIdAndProblemId(
            @Param("contestId") String contestId,
            @Param("problemId") Long problemId
    );

    @Select("SELECT COUNT(*) FROM contest_problems WHERE contest_id = #{contestId}")
    long countByContestId(@Param("contestId") String contestId);

    @Delete("DELETE FROM contest_problems WHERE contest_id = #{contestId}")
    int deleteByContestId(@Param("contestId") String contestId);

    @Select("SELECT * FROM contest_problems WHERE problem_id = #{problemId}")
    List<ContestProblem> findByProblemId(@Param("problemId") Long problemId);
}
