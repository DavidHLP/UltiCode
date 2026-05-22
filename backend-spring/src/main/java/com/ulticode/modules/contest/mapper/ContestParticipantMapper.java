package com.ulticode.modules.contest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.contest.entity.ContestParticipant;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
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
     * Count total contests a user has participated in.
     *
     * @param userId the user ID
     * @return count of contests the user has registered for
     */
    @Select("SELECT COUNT(*) FROM contest_participants WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") String userId);

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
    @Select("SELECT * FROM contest_participants WHERE contest_id = #{contestId} AND status = 'FINISHED' ORDER BY total_score DESC, total_penalty ASC LIMIT #{limit}")
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

    /**
     * DTO record holding ContestParticipant fields plus joined user data.
     */
    record ContestParticipantWithUser(
            String id,
            String contestId,
            String userId,
            String status,
            Integer finalRank,
            Integer totalScore,
            Integer totalPenalty,
            Integer totalTime,
            Integer attemptCount,
            String registeredAt,
            String updatedAt,
            String virtualSessionId,
            String username,
            String name,
            String avatar
    ) {}

    /**
     * Find participants by contest ID with user data joined.
     * Eliminates N+1 by fetching username/name/avatar in a single query.
     *
     * @param contestId the contest ID
     * @return list of participants with user data
     */
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "contest_id", property = "contestId"),
            @Result(column = "user_id", property = "userId"),
            @Result(column = "status", property = "status"),
            @Result(column = "final_rank", property = "finalRank"),
            @Result(column = "total_score", property = "totalScore"),
            @Result(column = "total_penalty", property = "totalPenalty"),
            @Result(column = "total_time", property = "totalTime"),
            @Result(column = "attempt_count", property = "attemptCount"),
            @Result(column = "registered_at", property = "registeredAt"),
            @Result(column = "updated_at", property = "updatedAt"),
            @Result(column = "virtual_session_id", property = "virtualSessionId"),
            @Result(column = "username", property = "username"),
            @Result(column = "name", property = "name"),
            @Result(column = "avatar", property = "avatar")
    })
    @Select("SELECT cp.*, u.username, u.name, u.avatar " +
            "FROM contest_participants cp " +
            "LEFT JOIN users u ON cp.user_id = u.id " +
            "WHERE cp.contest_id = #{contestId} " +
            "ORDER BY cp.final_rank ASC, cp.total_score DESC, cp.total_penalty ASC")
    List<ContestParticipantWithUser> selectParticipantsWithUserByContestId(@Param("contestId") String contestId);

    /**
     * Find all participants for a list of contest IDs.
     * Used by ContestScheduler to find participants for reminder notifications.
     *
     * @param contestIds list of contest IDs
     * @return list of participants for those contests
     */
    @Select("<script>SELECT * FROM contest_participants WHERE contest_id IN " +
            "<foreach item='item' collection='contestIds' open='(' separator=',' close=')'>" +
            "#{item}</foreach> ORDER BY contest_id, registered_at ASC</script>")
    List<ContestParticipant> findByContestIds(@Param("contestIds") List<String> contestIds);

    @Select("<script>SELECT * FROM contest_participants WHERE contest_id IN " +
            "<foreach item='item' collection='contestIds' open='(' separator=',' close=')'>" +
            "#{item}</foreach> AND user_id = #{userId}</script>")
    List<ContestParticipant> findByContestIdsAndUserId(@Param("contestIds") List<String> contestIds, @Param("userId") String userId);
}
