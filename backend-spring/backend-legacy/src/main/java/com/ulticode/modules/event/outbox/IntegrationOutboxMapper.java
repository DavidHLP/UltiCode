package com.ulticode.modules.event.outbox;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MyBatis-Plus mapper for {@link IntegrationOutboxRecord} (P6-OUTBOX-001).
 *
 * <p>Custom methods implement the CAS-claim + confirm-dispatch pattern
 * matching the existing {@code AuditOutboxMapper} and {@code JudgeOutboxMapper}.
 */
@Mapper
public interface IntegrationOutboxMapper extends BaseMapper<IntegrationOutboxRecord> {

    /**
     * Atomically claim up to {@code limit} PENDING rows by setting state=CLAIMED
     * and claimed_at=NOW(). Returns the claimed rows.
     *
     * <p>This follows the same pattern as {@code AuditOutboxMapper.claimPending}
     * and {@code JudgeOutboxMapper.claimPending}: a single UPDATE with
     * subquery to select IDs, then SELECT those rows.
     */
    @Update("""
        UPDATE integration_outbox SET
          state = 'CLAIMED',
          claimed_at = NOW(3)
        WHERE event_id IN (
          SELECT event_id FROM (
            SELECT event_id FROM integration_outbox
            WHERE state = 'PENDING' AND next_retry_at <= NOW(3)
            ORDER BY created_at
            LIMIT #{limit}
          ) AS claimable
        )
        """)
    int claimPending(@Param("limit") int limit);

    @Select("""
        SELECT * FROM integration_outbox
        WHERE state = 'CLAIMED'
          AND claimed_at >= DATE_SUB(NOW(3), INTERVAL 60 SECOND)
        ORDER BY created_at
        """)
    List<IntegrationOutboxRecord> selectClaimed();

    /**
     * Mark a row as DELIVERED after successful Redis Streams XADD.
     */
    @Update("""
        UPDATE integration_outbox SET
          state = 'DELIVERED',
          delivered_at = NOW(3),
          stream_id = #{streamId},
          last_error = NULL
        WHERE event_id = #{eventId}
        """)
    int markDelivered(@Param("eventId") String eventId, @Param("streamId") String streamId);

    /**
     * Mark a row as failed: increment attempts, set error, compute next retry.
     * If attempts >= maxAttempts, set state=DEAD.
     */
    @Update("""
        UPDATE integration_outbox SET
          state = CASE WHEN attempts + 1 >= #{maxAttempts} THEN 'DEAD' ELSE 'PENDING' END,
          attempts = attempts + 1,
          last_error = #{error},
          next_retry_at = DATE_ADD(NOW(3), INTERVAL POWER(2, attempts + 1) SECOND)
        WHERE event_id = #{eventId}
        """)
    int markFailed(@Param("eventId") String eventId, @Param("error") String error,
                   @Param("maxAttempts") int maxAttempts);

    /**
     * Get the oldest undelivered event age in seconds (for the oldest-outbox-age metric).
     */
    @Select("""
        SELECT TIMESTAMPDIFF(SECOND, MIN(created_at), NOW(3))
        FROM integration_outbox
        WHERE state IN ('PENDING', 'CLAIMED')
        """)
    Long oldestOutboxAgeSeconds();
}
