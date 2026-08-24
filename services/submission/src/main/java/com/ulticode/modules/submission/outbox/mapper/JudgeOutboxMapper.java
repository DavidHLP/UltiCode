package com.ulticode.modules.submission.outbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.submission.outbox.entity.JudgeOutboxRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MyBatis mapper for {@link JudgeOutboxRecord} (ADR-003 M3a).
 *
 * <p>The {@code claim} query uses {@code FOR UPDATE SKIP LOCKED} so that
 * multiple dispatcher instances (or a dispatcher racing a reaper) never process
 * the same row. MySQL 8.0+ is required.
 *
 * <p>Moved from {@code com.ulticode.modules.queue.outbox.mapper} during P7
 * submission-family cutover.
 */
@Mapper
public interface JudgeOutboxMapper extends BaseMapper<JudgeOutboxRecord> {

    /**
     * Claim a batch of pending rows whose retry time has arrived. Rows are
     * returned in dispatch order; the caller is responsible for sorting
     * when a global ordering is needed.
     *
     * <p>{@code FOR UPDATE SKIP LOCKED} lets multiple dispatchers race
     * without deadlocking: a dispatcher that cannot acquire a row's lock
     * immediately skips it and moves on.
     *
     * @param batchSize upper bound on rows to return
     * @return up to {@code batchSize} pending rows, locked by this transaction
     */
    @ResultMap("mybatis-plus_JudgeOutboxRecord")
    @Select("SELECT * FROM judge_outbox "
            + "WHERE state = 'PENDING' AND next_retry_at <= NOW() "
            + "ORDER BY next_retry_at "
            + "LIMIT #{batchSize} "
            + "FOR UPDATE SKIP LOCKED")
    List<JudgeOutboxRecord> claim(@Param("batchSize") int batchSize);

    /**
     * Mark a row as successfully dispatched. Clears the next-retry time and
     * transitions to {@code SENT}.
     *
     * @param id primary key of the row to mark
     * @return affected row count (1 = success, 0 = row already gone)
     */
    @Update("UPDATE judge_outbox "
            + "SET state = 'SENT', sent_at = NOW(), next_retry_at = NOW() "
            + "WHERE id = #{id}")
    int markSent(@Param("id") String id);

    /**
     * Record a failed dispatch attempt and schedule a backoff retry. Increments
     * {@code attempts}, records the error, and pushes {@code next_retry_at}
     * forward (exponential backoff is the caller's responsibility).
     *
     * @param id          primary key of the row to retry
     * @param nextRetryAt the timestamp at which the next attempt is allowed
     * @param error       truncated error message to persist
     * @return affected row count
     */
    @Update("UPDATE judge_outbox "
            + "SET state = 'PENDING', attempts = attempts + 1, "
            + "    last_error = #{error}, next_retry_at = #{nextRetryAt} "
            + "WHERE id = #{id}")
    int markRetry(@Param("id") String id,
                  @Param("nextRetryAt") LocalDateTime nextRetryAt,
                  @Param("error") String error);

    /**
     * Mark a row as dead — exhausted retries, parked for inspection. Does not
     * touch {@code next_retry_at} so the row stays parked indefinitely.
     *
     * @param id    primary key of the row to mark dead
     * @param error final error message to persist
     * @return affected row count
     */
    @Update("UPDATE judge_outbox "
            + "SET state = 'DEAD', last_error = #{error}, next_retry_at = NOW() "
            + "WHERE id = #{id}")
    int markDead(@Param("id") String id, @Param("error") String error);

    /**
     * Count rows in a given state. Used by the shadow comparator and ops
     * tooling to monitor the outbox health.
     *
     * @param state one of {@code PENDING}, {@code SENT}, {@code DEAD}, {@code ARCHIVED}
     * @return row count
     */
    @Select("SELECT COUNT(*) FROM judge_outbox WHERE state = #{state}")
    long countByState(@Param("state") String state);

    /**
     * Count rows for a specific submission, optionally filtered to shadow rows.
     * Read-only; no {@code FOR UPDATE}.
     *
     * @param submissionId the submission to look up
     * @return row count
     */
    @Select("SELECT COUNT(*) FROM judge_outbox WHERE submission_id = #{submissionId}")
    long countBySubmission(@Param("submissionId") String submissionId);

    /**
     * Select PENDING rows that have aged past the grace cutoff — the signature
     * of a worker that died before acquiring a lease. The reaper uses this to
     * revoke stuck leases via the submission fence port.
     *
     * @param staleBefore timestamp threshold; rows whose next_retry_at is before
     *                    this are considered abandoned
     * @return abandoned pending rows, oldest first
     */
    @ResultMap("mybatis-plus_JudgeOutboxRecord")
    @Select("SELECT * FROM judge_outbox "
            + "WHERE state = 'PENDING' AND next_retry_at < #{staleBefore} "
            + "ORDER BY next_retry_at")
    List<JudgeOutboxRecord> selectStalePending(@Param("staleBefore") LocalDateTime staleBefore);

    /**
     * Claim rows for real (non-shadow) dispatch (ADR-003 M3c-2 cutover).
     * Unlike {@link #claim}, this filters to {@code is_shadow = 0} rows created
     * at or after the cutover timestamp, so the M3c dispatcher never steals
     * rows from the M3a shadow path.
     *
     * @param batchSize  upper bound on rows to return
     * @param cutoverAt  only rows created at or after this timestamp
     * @return up to {@code batchSize} real-dispatch pending rows
     */
    @ResultMap("mybatis-plus_JudgeOutboxRecord")
    @Select("SELECT * FROM judge_outbox "
            + "WHERE state = 'PENDING' AND next_retry_at <= NOW() "
            + "  AND is_shadow = 0 AND created_at >= #{cutoverAt} "
            + "ORDER BY next_retry_at "
            + "LIMIT #{batchSize} "
            + "FOR UPDATE SKIP LOCKED")
    List<JudgeOutboxRecord> claimRealDispatch(@Param("batchSize") int batchSize,
                                              @Param("cutoverAt") LocalDateTime cutoverAt);

    /**
     * Count shadow rows older than the cutover watermark — candidates for
     * archival once the real-dispatch path is the only active producer
     * (F13 follow-up). Read-only.
     */
    @Select("SELECT COUNT(*) FROM judge_outbox "
            + "WHERE is_shadow = 1 AND created_at < #{cutoverAt}")
    long countStaleShadowRows(@Param("cutoverAt") LocalDateTime cutoverAt);
}
