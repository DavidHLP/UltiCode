package com.ulticode.modules.queue.outbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.submission.outbox.entity.JudgeOutboxRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MyBatis mapper for {@link JudgeOutboxRecord} (ADR-003 M3a).
 *
 * <p>The {@code claim} query uses {@code FOR UPDATE SKIP LOCKED} so that
 * multiple dispatcher instances (or a dispatcher racing a reaper) never process
 * the same row. MySQL 8.0+ is required; the project targets 9.1.
 */
@Mapper
public interface JudgeOutboxMapper extends BaseMapper<JudgeOutboxRecord> {

    /**
     * Claim a batch of pending rows whose retry time has arrived. Rows are
     * locked with {@code FOR UPDATE SKIP LOCKED}; the caller must run inside a
     * transaction and transition each claimed row to SENT / retry / DEAD before
     * committing.
     *
     * @param batchSize max rows to claim
     * @return claimed rows (empty when none ready)
     */
    @Select("SELECT * FROM judge_outbox "
            + "WHERE state = 'PENDING' AND next_retry_at <= NOW() "
            + "ORDER BY next_retry_at "
            + "LIMIT #{batchSize} "
            + "FOR UPDATE SKIP LOCKED")
    List<JudgeOutboxRecord> claim(@Param("batchSize") int batchSize);

    /**
     * Mark a row as successfully dispatched. Clears the next-retry time and
     * stamps the sent timestamp using the DB clock.
     *
     * @param id row id
     * @return affected rows (1 on success)
     */
    @Update("UPDATE judge_outbox "
            + "SET state = 'SENT', sent_at = NOW(), next_retry_at = NOW() "
            + "WHERE id = #{id}")
    int markSent(@Param("id") String id);

    /**
     * Record a failed dispatch attempt and schedule a backoff retry. Increments
     * the attempt counter and stores the (truncated) error message.
     *
     * @param id           row id
     * @param nextRetryAt  when to retry next (caller-computed backoff, DB clock)
     * @param error        truncated error message
     * @return affected rows (1 on success)
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
     * delete the row (the unique key must stay so a future re-enqueue at the
     * same generation is still deduped).
     *
     * @param id    row id
     * @param error final error message
     * @return affected rows (1 on success)
     */
    @Update("UPDATE judge_outbox "
            + "SET state = 'DEAD', last_error = #{error}, next_retry_at = NOW() "
            + "WHERE id = #{id}")
    int markDead(@Param("id") String id, @Param("error") String error);

    /**
     * Count rows in a given state. Used by the shadow comparator and ops
     * dashboards to detect backlog growth.
     *
     * @param state target state
     * @return row count
     */
    @Select("SELECT COUNT(*) FROM judge_outbox WHERE state = #{state}")
    long countByState(@Param("state") String state);

    /**
     * Count rows for a specific submission, optionally filtered to shadow rows.
     * Used by integration tests to assert the outbox was written.
     *
     * @param submissionId submission id
     * @return row count
     */
    @Select("SELECT COUNT(*) FROM judge_outbox WHERE submission_id = #{submissionId}")
    long countBySubmission(@Param("submissionId") String submissionId);

    /**
     * Select PENDING rows that have aged past the grace cutoff — the signature
     * of a stalled dispatcher or a missed real enqueue (ADR-005 F8). Drives the
     * {@code outbox.shadow.diff} metric via
     * {@link com.ulticode.modules.queue.outbox.shadow.OutboxShadowComparator}.
     *
     * <p>Read-only; does not lock. The dispatcher's own claim handles locking.
     *
     * @param staleBefore cutoff timestamp (rows older than this count as stale)
     * @return stale PENDING rows
     */
    @Select("SELECT * FROM judge_outbox "
            + "WHERE state = 'PENDING' AND next_retry_at < #{staleBefore} "
            + "ORDER BY next_retry_at")
    List<JudgeOutboxRecord> selectStalePending(@Param("staleBefore") LocalDateTime staleBefore);

    /**
     * Claim rows for real (non-shadow) dispatch (ADR-003 M3c-2 cutover).
     * Filters out {@code is_shadow = 1} rows (M3a/M3b legacy) and rows
     * written before the cutover watermark so the real dispatcher cannot
     * double-dispatch a row the shadow dispatcher already observed.
     *
     * <p>Locking and batch size mirror {@link #claim(int)}.
     *
     * @param batchSize   max rows to claim
     * @param cutoverAt   watermark — only rows with {@code created_at >= cutoverAt}
     *                    are eligible (F13)
     * @return real-dispatch-eligible rows
     */
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
