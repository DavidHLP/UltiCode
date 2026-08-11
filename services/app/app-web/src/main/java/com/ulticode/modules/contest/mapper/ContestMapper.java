package com.ulticode.modules.contest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.contest.entity.Contest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MyBatis-Plus mapper for Contest entity.
 * Extends BaseMapper for basic CRUD operations and provides custom query methods.
 */
@Mapper
public interface ContestMapper extends BaseMapper<Contest> {

    /**
     * Find contests by status.
     *
     * @param status the contest status to filter by
     * @return list of contests with the given status, ordered by start time
     */
    @Select("SELECT * FROM contests WHERE status = #{status} AND is_deleted = 0 ORDER BY start_time ASC")
    List<Contest> findByStatus(@Param("status") String status);

    /**
     * Lock a contest row while validating submission admission. The lock keeps
     * a concurrent lifecycle transition from changing the status between the
     * admission check and the contest-submission insert.
     */
    @Select("SELECT * FROM contests WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    Contest selectByIdForUpdate(@Param("id") String id);

    /**
     * Lock a contest for the owner deletion seam, including an already
     * soft-deleted row so a retry can finish relational cleanup.
     */
    @Select("SELECT * FROM contests WHERE id = #{id} LIMIT 1 FOR UPDATE")
    Contest selectByIdIncludingDeletedForUpdate(@Param("id") String id);

    /**
     * Find contests by creator.
     *
     * @param createdBy the user ID of the creator
     * @return list of contests created by the user, ordered by creation time
     */
    @Select("SELECT * FROM contests WHERE created_by = #{createdBy} AND is_deleted = 0 ORDER BY created_at DESC")
    List<Contest> findByCreatedBy(@Param("createdBy") String createdBy);

    /**
     * Find active contests (running or upcoming).
     *
     * @return list of active and visible contests, ordered by start time
     */
    @Select("SELECT * FROM contests WHERE status IN ('UPCOMING', 'RUNNING') AND is_deleted = 0 AND is_visible = 1 ORDER BY start_time ASC")
    List<Contest> findActiveContests();

    /**
     * Find contests starting within a time range.
     *
     * @param startTime the start of the time range
     * @param endTime   the end of the time range
     * @return list of contests starting within the range, ordered by start time
     */
    @Select("SELECT * FROM contests WHERE start_time BETWEEN #{startTime} AND #{endTime} AND is_deleted = 0 ORDER BY start_time ASC")
    List<Contest> findByStartTimeBetween(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * Find contest by slug.
     *
     * @param slug the unique slug identifier
     * @return the contest if found, null otherwise
     */
    @Select("SELECT * FROM contests WHERE slug = #{slug} AND is_deleted = 0 LIMIT 1")
    Contest findBySlug(@Param("slug") String slug);

    /**
     * Count contests by status.
     *
     * @param status the contest status to filter by
     * @return count of contests with the given status
     */
    @Select("SELECT COUNT(*) FROM contests WHERE status = #{status} AND is_deleted = 0")
    long countByStatus(@Param("status") String status);

    /**
     * Update contest status.
     *
     * @param id     the contest ID
     * @param status the new status
     * @return number of rows affected
     */
    @Update("UPDATE contests SET status = #{status}, updated_at = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") String id, @Param("status") String status);

    /**
     * Atomically claim the UPCOMING → RUNNING transition. The {@code status}
     * predicate is the concurrency invariant: under multiple replicas or
     * retrying ticks, exactly one caller gets affected=1; the rest get 0 and
     * must skip the transition and all of its side effects. Returns the
     * affected-row count so the caller can gate emission, ranking, and rating.
     *
     * @param id  the contest ID
     * @param now the actual start time to stamp
     * @return 1 if this caller claimed the transition, 0 if it was already moved
     */
    @Update("UPDATE contests SET status = 'RUNNING', actual_start_time = #{now}, updated_at = NOW() "
            + "WHERE id = #{id} AND status = 'UPCOMING' AND is_deleted = 0")
    int tryTransitionToRunning(@Param("id") String id, @Param("now") LocalDateTime now);

    /**
     * Return a partially-started contest to UPCOMING when a post-claim side
     * effect fails. The actual start timestamp fences this compensation from
     * a later state transition.
     */
    @Update("UPDATE contests SET status = 'UPCOMING', actual_start_time = NULL, updated_at = NOW() "
            + "WHERE id = #{id} AND status = 'RUNNING' AND is_deleted = 0 "
            + "AND actual_start_time = #{now}")
    int revertRunningToUpcoming(@Param("id") String id, @Param("now") LocalDateTime now);

    /**
     * Atomically claim the RUNNING → FINISHING transition. The intermediate
     * state keeps a claimed contest visible to the retry loop until every
     * finalization side effect succeeds.
     *
     * @param id  the contest ID
     * @param now the actual end time to stamp
     * @return 1 if this caller claimed the transition, 0 if it was already moved
     */
    @Update("UPDATE contests SET status = 'FINISHING', actual_end_time = COALESCE(actual_end_time, #{now}), "
            + "updated_at = NOW() WHERE id = #{id} AND status = 'RUNNING' AND is_deleted = 0")
    int tryTransitionToFinishing(@Param("id") String id, @Param("now") LocalDateTime now);

    /**
     * Publish FINISHED only after the retry-safe finalizer has completed all
     * participant, ranking, notification and rating side effects.
     *
     * @param id  the contest ID
     * @param now fallback end time for rows created before the claim timestamp
     * @return 1 if this caller published FINISHED, 0 if another retry did so
     */
    @Update("UPDATE contests SET status = 'FINISHED', actual_end_time = COALESCE(actual_end_time, #{now}), "
            + "updated_at = NOW() WHERE id = #{id} AND status = 'FINISHING' AND is_deleted = 0")
    int tryFinalizeFinished(@Param("id") String id, @Param("now") LocalDateTime now);

    /**
     * Atomically increment registered count.
     *
     * @param contestId the contest ID
     * @return number of rows affected
     */
    @Update("UPDATE contests SET registered_count = registered_count + 1, updated_at = NOW() WHERE id = #{contestId}")
    int incrementRegisteredCount(@Param("contestId") String contestId);

    /**
     * Atomically increment registered count only if not full.
     * Uses conditional UPDATE to prevent race conditions.
     *
     * @param contestId the contest ID
     * @return number of rows affected (0 means contest is full)
     */
    @Update("UPDATE contests SET registered_count = registered_count + 1, updated_at = NOW() "
            + "WHERE id = #{contestId} AND (max_participants IS NULL OR registered_count < max_participants)")
    int tryIncrementRegisteredCount(@Param("contestId") String contestId);

    /**
     * Atomically decrement registered count (minimum 0).
     *
     * @param contestId the contest ID
     * @return number of rows affected
     */
    @Update("UPDATE contests SET registered_count = GREATEST(registered_count - 1, 0), updated_at = NOW() WHERE id = #{contestId}")
    int decrementRegisteredCount(@Param("contestId") String contestId);

    /**
     * Atomically increment participant count.
     *
     * @param contestId the contest ID
     * @return number of rows affected
     */
    @Update("UPDATE contests SET participant_count = participant_count + 1, updated_at = NOW() WHERE id = #{contestId}")
    int incrementParticipantCount(@Param("contestId") String contestId);

    /**
     * Atomically increment submission count.
     *
     * @param contestId the contest ID
     * @return number of rows affected
     */
    @Update("UPDATE contests SET submission_count = submission_count + 1, updated_at = NOW() WHERE id = #{contestId}")
    int incrementSubmissionCount(@Param("contestId") String contestId);
}
