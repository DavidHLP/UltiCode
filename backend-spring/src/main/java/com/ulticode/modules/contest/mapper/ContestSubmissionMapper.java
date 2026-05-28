package com.ulticode.modules.contest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.contest.entity.ContestSubmission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * MyBatis-Plus mapper for ContestSubmission entity.
 */
@Mapper
public interface ContestSubmissionMapper extends BaseMapper<ContestSubmission> {

    @Select("SELECT * FROM contest_submissions WHERE contest_id = #{contestId} AND participant_id = #{participantId} ORDER BY submitted_at ASC")
    List<ContestSubmission> findByContestIdAndParticipantId(
            @Param("contestId") String contestId,
            @Param("participantId") String participantId
    );

    @Select("SELECT COUNT(*) FROM contest_submissions WHERE contest_id = #{contestId}")
    long countByContestId(@Param("contestId") String contestId);

    @Select("SELECT COUNT(*) FROM contest_submissions")
    long countTotal();
}
