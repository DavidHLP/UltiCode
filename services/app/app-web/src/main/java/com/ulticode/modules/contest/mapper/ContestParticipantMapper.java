package com.ulticode.modules.contest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.contest.entity.ContestParticipant;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;
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
    @Select("SELECT * FROM contest_participants WHERE contest_id = #{contestId} AND is_virtual = 0 ORDER BY final_rank ASC, total_score DESC, total_penalty ASC")
    List<ContestParticipant> findByContestId(@Param("contestId") String contestId);

    /**
     * Find participant by contest ID and user ID.
     *
     * @param contestId the contest ID
     * @param userId    the user ID
     * @return the participant if found
     */
    @Select("SELECT * FROM contest_participants WHERE contest_id = #{contestId} "
            + "AND user_id = #{userId} AND is_virtual = 0 ORDER BY registered_at DESC LIMIT 1")
    Optional<ContestParticipant> findByContestIdAndUserId(
            @Param("contestId") String contestId,
            @Param("userId") String userId
    );

    /** Read the canonical real registration; virtual replay rows are separate data. */
    @Select("SELECT * FROM contest_participants WHERE contest_id = #{contestId} "
            + "AND user_id = #{userId} AND is_virtual = 0 "
            + "ORDER BY registered_at DESC LIMIT 1")
    Optional<ContestParticipant> findRealByContestIdAndUserId(
            @Param("contestId") String contestId,
            @Param("userId") String userId
    );

    /** Find the latest virtual participation for a contest and user. */
    @Select("SELECT * FROM contest_participants WHERE contest_id = #{contestId} "
            + "AND user_id = #{userId} AND is_virtual = 1 "
            + "ORDER BY registered_at DESC LIMIT 1")
    Optional<ContestParticipant> findVirtualByContestIdAndUserId(
            @Param("contestId") String contestId,
            @Param("userId") String userId
    );

    /**
     * Lock the participant while unregistering so lifecycle transitions and
     * cascade deletes cannot invalidate the row between validation and delete.
     */
    @Select("SELECT * FROM contest_participants "
            + "WHERE contest_id = #{contestId} AND user_id = #{userId} "
            + "AND is_virtual = 0 ORDER BY registered_at DESC LIMIT 1 FOR UPDATE")
    Optional<ContestParticipant> findByContestIdAndUserIdForUpdate(
            @Param("contestId") String contestId,
            @Param("userId") String userId
    );

    /**
     * Lock the real participant used by explicit contest admission. Keeping
     * {@code is_virtual = 0} in SQL avoids selecting a finished/active virtual
     * replay when a user has both kinds of participation rows.
     */
    @Select("SELECT * FROM contest_participants "
            + "WHERE contest_id = #{contestId} AND user_id = #{userId} "
            + "AND is_virtual = 0 ORDER BY registered_at DESC LIMIT 1 FOR UPDATE")
    Optional<ContestParticipant> findRealForSubmissionAdmission(
            @Param("contestId") String contestId,
            @Param("userId") String userId
    );

    /**
     * Lock the exact virtual session used by explicit contest admission.
     */
    @Select("SELECT * FROM contest_participants "
            + "WHERE contest_id = #{contestId} AND user_id = #{userId} "
            + "AND is_virtual = 1 AND virtual_session_id = #{virtualSessionId} "
            + "LIMIT 1 FOR UPDATE")
    Optional<ContestParticipant> findVirtualForSubmissionAdmission(
            @Param("contestId") String contestId,
            @Param("userId") String userId,
            @Param("virtualSessionId") String virtualSessionId
    );

    /**
     * Lock one participant while aggregate score and penalty fields are updated.
     */
    @Select("SELECT * FROM contest_participants WHERE id = #{id} FOR UPDATE")
    ContestParticipant selectByIdForUpdate(@Param("id") String id);

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
     * Count participants in publicly visible, non-deleted contests by status.
     *
     * @param status the participant status
     * @return public participant count with the given status
     */
    @Select("SELECT COUNT(*) FROM contest_participants cp "
            + "JOIN contests c ON c.id = cp.contest_id "
            + "WHERE cp.status = #{status} AND c.is_visible = 1 AND c.is_deleted = 0")
    long countByStatus(@Param("status") String status);

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
     * Start every REGISTERED participant when its contest begins.
     * The status guard and timestamp write remain one atomic database command.
     */
    @Update("UPDATE contest_participants SET status = 'STARTED', "
            + "started_at = COALESCE(started_at, #{now}), "
            + "updated_at = NOW() "
            + "WHERE contest_id = #{contestId} AND status = 'REGISTERED'")
    int startRegisteredParticipants(@Param("contestId") String contestId,
                                    @Param("now") java.time.LocalDateTime now);

    /**
     * R3.1: finish all REAL (is_virtual = 0) participants of a contest that are
     * still STARTED. Used by the scheduler when a real contest transitions to
     * FINISHED so we can compute ratings over a closed set.
     *
     * @param contestId the contest id
     * @param now       the timestamp to stamp on finished_at
     * @return number of rows transitioned
     */
    @Update("UPDATE contest_participants SET status = 'FINISHED', "
            + "finished_at = #{now}, updated_at = NOW() "
            + "WHERE contest_id = #{contestId} AND status = 'STARTED' AND is_virtual = 0")
    int finishStartedRealParticipants(@Param("contestId") String contestId,
                                      @Param("now") java.time.LocalDateTime now);

    /**
     * M2: bulk-finish a set of virtual participants (looked up by
     * {@link #findVirtualParticipantsToFinish}) in a single UPDATE. Replaces
     * the per-row N+1 previously in {@code autoFinishVirtualParticipants}.
     *
     * @param ids participant ids to finish
     * @param now timestamp to stamp on finished_at
     * @return number of rows transitioned
     */
    @Update("<script>UPDATE contest_participants "
            + "SET status = 'FINISHED', finished_at = #{now}, updated_at = NOW() "
            + "WHERE id IN "
            + "<foreach item='id' collection='ids' open='(' separator=',' close=')'>"
            + "#{id}</foreach> "
            + "AND status = 'STARTED' AND is_virtual = 1</script>")
    int finishStartedVirtualParticipantsByIds(
            @Param("ids") java.util.Collection<String> ids,
            @Param("now") java.time.LocalDateTime now);

    /**
     * R3.2: fetch all real (non-virtual) participants of a contest for the
     * rating calculation. Replaces the status=STARTED heuristic that breaks
     * once R3.1 starts marking real participants FINISHED on contest end.
     */
    @Select("SELECT * FROM contest_participants WHERE contest_id = #{contestId} AND is_virtual = 0")
    List<ContestParticipant> findRealParticipantsByContestId(@Param("contestId") String contestId);

    /**
     * R3.3: locate an active virtual session for a user in a contest, with
     * {@code FOR UPDATE} row lock so concurrent {@code startVirtualContest}
     * calls serialize. Returns the active session (is_virtual=1, status=STARTED)
     * or empty.
     */
    @Select("SELECT * FROM contest_participants "
            + "WHERE contest_id = #{contestId} AND user_id = #{userId} "
            + "AND is_virtual = 1 AND status = 'STARTED' "
            + "ORDER BY registered_at DESC LIMIT 1 FOR UPDATE")
    Optional<ContestParticipant> findActiveVirtualSessionForUpdate(
            @Param("contestId") String contestId,
            @Param("userId") String userId);

    /**
     * R6.3 / F-08: tells the contest integration layer whether a given
     * submission belongs to a virtual session. Returns true only if there is a
     * contest_submissions row pointing at a contest_participants row with
     * {@code is_virtual=1}. Used by {@code ContestSubmissionAdapter} to skip
     * achievement triggers for virtual ACs.
     */
    @Select("SELECT cp.is_virtual FROM contest_submissions cs "
            + "JOIN contest_participants cp ON cs.participant_id = cp.id "
            + "WHERE cs.submission_id = #{submissionId} LIMIT 1")
    Optional<Boolean> findIsVirtualBySubmissionId(@Param("submissionId") String submissionId);

    /**
     * R9.1 / F-24: keyset pagination. Returns the next page of
     * ranked participants for a contest, starting after
     * {@code (afterRank, afterUserId)} (exclusive). {@code afterRank}
     * is null for the first page; the &lt;choose&gt; branch handles
     * the first-page case explicitly so the query does not
     * degenerate to NULL > NULL (which evaluates to UNKNOWN and
     * returns zero rows — the bug R8 review caught in the
     * pre-removal version).
     */
    @Select("<script>"
            + "SELECT cp.id, cp.contest_id, cp.user_id, cp.status, cp.final_rank, "
            + "cp.total_score, cp.total_penalty, cp.total_time, cp.attempt_count, "
            + "cp.registered_at, cp.updated_at, cp.virtual_session_id, "
            + "NULL AS username, NULL AS name, NULL AS avatar, cp.last_solve_time, "
            + "(SELECT COUNT(*) FROM contest_problem_results cpr "
            + "WHERE cpr.participant_id = cp.id AND cpr.is_solved = 1) AS problems_solved "
            + "FROM contest_participants cp "
            + "WHERE cp.contest_id = #{contestId} AND cp.is_virtual = 0 "
            + "AND cp.final_rank IS NOT NULL "
            + "<choose>"
            + "<when test='afterRank == null'>"
            + "ORDER BY cp.final_rank ASC, cp.user_id ASC "
            + "</when>"
            + "<otherwise>"
            + "AND (cp.final_rank > #{afterRank} "
            + "     OR (cp.final_rank = #{afterRank} AND cp.user_id > #{afterUserId})) "
            + "ORDER BY cp.final_rank ASC, cp.user_id ASC "
            + "</otherwise>"
            + "</choose>"
            + "LIMIT #{limit}"
            + "</script>")
    List<ContestParticipantWithUser> selectParticipantsKeyset(
            @Param("contestId") String contestId,
            @Param("afterRank") Integer afterRank,
            @Param("afterUserId") String afterUserId,
            @Param("limit") int limit);

    /**
     * Find virtual participants whose time has expired
     * ({@code started_at + duration_minutes < now}). P2-2 fix.
     */
    @Select("SELECT cp.* FROM contest_participants cp "
            + "JOIN contests c ON cp.contest_id = c.id "
            + "WHERE cp.is_virtual = 1 AND cp.status = 'STARTED' "
            + "AND c.duration_minutes IS NOT NULL "
            + "AND TIMESTAMPADD(MINUTE, c.duration_minutes, cp.started_at) < #{now}")
    List<ContestParticipant> findVirtualParticipantsToFinish(@Param("now") java.time.LocalDateTime now);

    /**
     * Cascade-delete all participants for a contest (used by deleteContestCascade).
     */
    @Delete("DELETE FROM contest_participants WHERE contest_id = #{contestId}")
    int deleteByContestId(@Param("contestId") String contestId);

    /**
     * Find top N participants by score in a contest.
     *
     * @param contestId the contest ID
     * @param limit     maximum number of participants to return
     * @return list of top participants
     */
    @Select("SELECT * FROM contest_participants WHERE contest_id = #{contestId} AND status = 'FINISHED' AND is_virtual = 0 ORDER BY total_score DESC, total_penalty ASC LIMIT #{limit}")
    List<ContestParticipant> findTopParticipants(
            @Param("contestId") String contestId,
            @Param("limit") int limit
    );

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
     * DTO record holding ContestParticipant fields. User display fields are
     * populated by the consumer-owned SubmissionUserReadPort after this query.
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
            java.time.LocalDateTime registeredAt,
            java.time.LocalDateTime updatedAt,
            String virtualSessionId,
            String username,
            String name,
            String avatar,
            Integer lastSolveTime,
            Integer problemsSolved
    ) {
        public ContestParticipantWithUser(
                String id,
                String contestId,
                String userId,
                String status,
                Integer finalRank,
                Integer totalScore,
                Integer totalPenalty,
                Integer totalTime,
                Integer attemptCount,
                java.time.LocalDateTime registeredAt,
                java.time.LocalDateTime updatedAt,
                String virtualSessionId,
                String username,
                String name,
                String avatar) {
            this(id, contestId, userId, status, finalRank, totalScore, totalPenalty, totalTime,
                    attemptCount, registeredAt, updatedAt, virtualSessionId, username, name, avatar,
                    null, null);
        }
    }

    /**
     * Find ranked participants by contest ID. User display data is resolved
     * through the consumer-owned SubmissionUserReadPort, not a cross-owner join.
     *
     * @param contestId the contest ID
     * @return list of participants with user data
     */
    @ConstructorArgs({
            @Arg(column = "id", javaType = String.class),
            @Arg(column = "contest_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "final_rank", javaType = Integer.class),
            @Arg(column = "total_score", javaType = Integer.class),
            @Arg(column = "total_penalty", javaType = Integer.class),
            @Arg(column = "total_time", javaType = Integer.class),
            @Arg(column = "attempt_count", javaType = Integer.class),
            @Arg(column = "registered_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "updated_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "virtual_session_id", javaType = String.class),
            @Arg(column = "username", javaType = String.class),
            @Arg(column = "name", javaType = String.class),
            @Arg(column = "avatar", javaType = String.class),
            @Arg(column = "last_solve_time", javaType = Integer.class),
            @Arg(column = "problems_solved", javaType = Integer.class)
    })
    @Select("SELECT cp.id, cp.contest_id, cp.user_id, cp.status, cp.final_rank, " +
            "cp.total_score, cp.total_penalty, cp.total_time, cp.attempt_count, " +
            "cp.registered_at, cp.updated_at, cp.virtual_session_id, " +
            "NULL AS username, NULL AS name, NULL AS avatar, cp.last_solve_time, " +
            "(SELECT COUNT(*) FROM contest_problem_results cpr " +
            "WHERE cpr.participant_id = cp.id AND cpr.is_solved = 1) AS problems_solved " +
            "FROM contest_participants cp " +
            "WHERE cp.contest_id = #{contestId} AND cp.is_virtual = 0 " +
            "ORDER BY cp.final_rank ASC, cp.total_score DESC, cp.total_penalty ASC")
    List<ContestParticipantWithUser> selectParticipantsWithUserByContestId(@Param("contestId") String contestId);

    /**
     * Find ranked participants by contest ID, paginated. User display data is
     * resolved through the consumer-owned SubmissionUserReadPort.
     *
     * @param contestId the contest ID
     * @param limit     maximum number of participants to return
     * @param offset    number of participants to skip
     * @return paginated list of ranked participants with user data
     */
    @ConstructorArgs({
            @Arg(column = "id", javaType = String.class),
            @Arg(column = "contest_id", javaType = String.class),
            @Arg(column = "user_id", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "final_rank", javaType = Integer.class),
            @Arg(column = "total_score", javaType = Integer.class),
            @Arg(column = "total_penalty", javaType = Integer.class),
            @Arg(column = "total_time", javaType = Integer.class),
            @Arg(column = "attempt_count", javaType = Integer.class),
            @Arg(column = "registered_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "updated_at", javaType = java.time.LocalDateTime.class),
            @Arg(column = "virtual_session_id", javaType = String.class),
            @Arg(column = "username", javaType = String.class),
            @Arg(column = "name", javaType = String.class),
            @Arg(column = "avatar", javaType = String.class),
            @Arg(column = "last_solve_time", javaType = Integer.class),
            @Arg(column = "problems_solved", javaType = Integer.class)
    })
    @Select("SELECT cp.id, cp.contest_id, cp.user_id, cp.status, cp.final_rank, " +
            "cp.total_score, cp.total_penalty, cp.total_time, cp.attempt_count, " +
            "cp.registered_at, cp.updated_at, cp.virtual_session_id, " +
            "NULL AS username, NULL AS name, NULL AS avatar, cp.last_solve_time, " +
            "(SELECT COUNT(*) FROM contest_problem_results cpr " +
            "WHERE cpr.participant_id = cp.id AND cpr.is_solved = 1) AS problems_solved " +
            "FROM contest_participants cp " +
            "WHERE cp.contest_id = #{contestId} AND cp.is_virtual = 0 AND cp.final_rank IS NOT NULL " +
            "ORDER BY cp.final_rank ASC, cp.total_score DESC, cp.total_penalty ASC " +
            "LIMIT #{limit} OFFSET #{offset}")
    List<ContestParticipantWithUser> selectParticipantsWithUserByContestIdPaginated(
            @Param("contestId") String contestId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    /**
     * Count ranked participants for a contest.
     *
     * @param contestId the contest ID
     * @return count of participants with a final rank
     */
    @Select("SELECT COUNT(*) FROM contest_participants " +
            "WHERE contest_id = #{contestId} AND final_rank IS NOT NULL")
    long countRankedParticipantsByContestId(@Param("contestId") String contestId);

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

    @Select("<script>SELECT * FROM contest_participants WHERE contest_id IN "
            + "<foreach collection='contestIds' item='item' open='(' separator=',' close=')'>"
            + "#{item}</foreach> AND user_id = #{userId} AND is_virtual = 0</script>")
    List<ContestParticipant> findByContestIdsAndUserId(@Param("contestIds") List<String> contestIds, @Param("userId") String userId);

    /**
     * Batch count participants grouped by contest ID.
     * Replaces N+1 per-contest count queries with a single GROUP BY query.
     * Uses MyBatis safe parameter binding via {@code <foreach>} to prevent SQL injection.
     *
     * <p><b>Precondition:</b> {@code contestIds} must be non-empty. An empty list
     * produces the SQL fragment {@code WHERE contest_id IN ()} which is a
     * syntax error in MySQL. Callers must short-circuit before invoking this
     * method when there are no contest IDs to look up.</p>
     *
     * @param contestIds non-empty list of contest IDs (UUIDs)
     * @return list of rows with contest_id, participant_count
     * @throws org.springframework.dao.DataAccessException if the generated SQL is invalid
     */
    @Select("<script>SELECT contest_id, COUNT(DISTINCT user_id) AS participant_count " +
            "FROM contest_participants " +
            "WHERE contest_id IN " +
            "<foreach item='item' collection='contestIds' open='(' separator=',' close=')'>" +
            "#{item}</foreach> " +
            "GROUP BY contest_id</script>")
    List<Map<String, Object>> countParticipantsByContestIds(@Param("contestIds") List<String> contestIds);

    /**
     * Batch count solved contest problems for user-history projections.
     * Callers must skip empty participant-id lists.
     */
    @Select("<script>SELECT participant_id AS participantId, COUNT(*) AS solvedCount " +
            "FROM contest_problem_results " +
            "WHERE participant_id IN " +
            "<foreach item='item' collection='participantIds' open='(' separator=',' close=')'>" +
            "#{item}</foreach> AND is_solved = 1 " +
            "GROUP BY participant_id</script>")
    List<Map<String, Object>> countSolvedByParticipantIds(
            @Param("participantIds") List<String> participantIds);
}
