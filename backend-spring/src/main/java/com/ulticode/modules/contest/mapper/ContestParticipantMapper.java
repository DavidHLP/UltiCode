package com.ulticode.modules.contest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.contest.entity.ContestParticipant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis-Plus mapper for ContestParticipant entity.
 * Extends BaseMapper for basic CRUD operations and provides custom query methods.
 */
@Mapper
public interface ContestParticipantMapper extends BaseMapper<ContestParticipant> {

    /**
     * Find participants by contest ID.
     *
     * @param contestId the contest ID
     * @return list of participants ordered by rank, score, and penalty
     */
    @Select("SELECT * FROM contest_participants WHERE contest_id = #{contestId} ORDER BY final_rank ASC, total_score DESC, total_penalty ASC")
    List<ContestParticipant> findByContestId(@Param("contestId") String contestId);

    /**
     * Find participant by contest ID and user ID.
     *
     * @param contestId the contest ID
     * @param userId    the user ID
     * @return the participant if found
     */
    @Select("SELECT * FROM contest_participants WHERE contest_id = #{contestId} AND user_id = #{userId} LIMIT 1")
    Optional<ContestParticipant> findByContestIdAndUserId(
            @Param("contestId") String contestId,
            @Param("userId") String userId
    );

    /**
     * Find participants by user ID.
     *
     * @param userId the user ID
     * @return list of participations ordered by registration time
     */
    @Select("SELECT * FROM contest_participants WHERE user_id = #{userId} ORDER BY registered_at DESC")
    List<ContestParticipant> findByUserId(@Param("userId") String userId);

    /**
     * Find participants by contest ID and status.
     *
     * @param contestId the contest ID
     * @param status    the participant status
     * @return list of participants with the given status
     */
    @Select("SELECT * FROM contest_participants WHERE contest_id = #{contestId} AND status = #{status} ORDER BY registered_at ASC")
    List<ContestParticipant> findByContestIdAndStatus(
            @Param("contestId") String contestId,
            @Param("status") String status
    );

    /**
     * Count participants by contest ID.
     *
     * @param contestId the contest ID
     * @return count of participants
     */
    @Select("SELECT COUNT(*) FROM contest_participants WHERE contest_id = #{contestId}")
    long countByContestId(@Param("contestId") String contestId);

    /**
     * Count participants by contest ID and status.
     *
     * @param contestId the contest ID
     * @param status    the participant status
     * @return count of participants with the given status
     */
    @Select("SELECT COUNT(*) FROM contest_participants WHERE contest_id = #{contestId} AND status = #{status}")
    long countByContestIdAndStatus(
            @Param("contestId") String contestId,
            @Param("status") String status
    );

    /**
     * Check if user is registered for a contest.
     *
     * @param contestId the contest ID
     * @param userId    the user ID
     * @return true if user is registered
     */
    @Select("SELECT COUNT(*) > 0 FROM contest_participants WHERE contest_id = #{contestId} AND user_id = #{userId}")
    boolean existsByContestIdAndUserId(
            @Param("contestId") String contestId,
            @Param("userId") String userId
    );

    /**
     * Find top N participants by score in a contest.
     *
     * @param contestId the contest ID
     * @param limit     maximum number of participants to return
     * @return list of top participants
     */
    @Select("SELECT * FROM contest_participants WHERE contest_id = #{contestId} AND status = 'COMPLETED' ORDER BY total_score DESC, total_penalty ASC LIMIT #{limit}")
    List<ContestParticipant> findTopParticipants(
            @Param("contestId") String contestId,
            @Param("limit") int limit
    );

    /**
     * Update participant status.
     *
     * @param id     the participant ID
     * @param status the new status
     * @return number of rows affected
     */
    @Update("UPDATE contest_participants SET status = #{status}, updated_at = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") String id, @Param("status") String status);

    /**
     * Update participant rank and scores.
     *
     * @param id           the participant ID
     * @param finalRank    the final rank
     * @param totalScore   the total score
     * @param totalPenalty the total penalty
     * @param totalTime    the total time
     * @return number of rows affected
     */
    @Update("UPDATE contest_participants SET final_rank = #{finalRank}, total_score = #{totalScore}, total_penalty = #{totalPenalty}, total_time = #{totalTime}, updated_at = NOW() WHERE id = #{id}")
    int updateRankAndScores(
            @Param("id") String id,
            @Param("finalRank") Integer finalRank,
            @Param("totalScore") Integer totalScore,
            @Param("totalPenalty") Integer totalPenalty,
            @Param("totalTime") Integer totalTime
    );

    /**
     * Find virtual participants by virtual session ID.
     *
     * @param virtualSessionId the virtual session ID
     * @return list of virtual participants
     */
    @Select("SELECT * FROM contest_participants WHERE virtual_session_id = #{virtualSessionId} ORDER BY registered_at ASC")
    List<ContestParticipant> findByVirtualSessionId(@Param("virtualSessionId") String virtualSessionId);
}
