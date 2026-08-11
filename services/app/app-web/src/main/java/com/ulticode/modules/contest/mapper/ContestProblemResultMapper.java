package com.ulticode.modules.contest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.contest.entity.ContestProblemResult;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis-Plus mapper for ContestProblemResult entity.
 */
@Mapper
public interface ContestProblemResultMapper extends BaseMapper<ContestProblemResult> {

    @Select("SELECT * FROM contest_problem_results WHERE participant_id = #{participantId} "
            + "AND contest_problem_id = #{contestProblemId} LIMIT 1")
    Optional<ContestProblemResult> findByParticipantIdAndContestProblemId(
            @Param("participantId") String participantId,
            @Param("contestProblemId") String contestProblemId
    );

    /**
     * Lock an existing per-problem result while an accepted verdict changes it.
     */
    @Select("SELECT * FROM contest_problem_results WHERE participant_id = #{participantId} "
            + "AND contest_problem_id = #{contestProblemId} LIMIT 1 FOR UPDATE")
    Optional<ContestProblemResult> findByParticipantIdAndContestProblemIdForUpdate(
            @Param("participantId") String participantId,
            @Param("contestProblemId") String contestProblemId
    );

    @Select("SELECT * FROM contest_problem_results WHERE contest_id = #{contestId}")
    List<ContestProblemResult> findByContestId(@Param("contestId") String contestId);

    @Delete("DELETE FROM contest_problem_results WHERE contest_id = #{contestId}")
    int deleteByContestId(@Param("contestId") String contestId);
}
