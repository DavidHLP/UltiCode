package com.ulticode.modules.event.outbox;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
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
     * Insert an event with a caller-owned idempotency key.
     *
     * <p>Result outbox retries reuse the source row ID, so a crash after this
     * insert but before the source row is acknowledged cannot create a second
     * integration event.
     */
    @Insert("""
        INSERT INTO integration_outbox
          (event_id, owner, aggregate_id, aggregate_version, causation_id, trace_id,
           event_type, schema_version, payload, state, attempts, created_at)
        VALUES
          (#{record.eventId}, #{record.owner}, #{record.aggregateId}, #{record.aggregateVersion},
           #{record.causationId}, #{record.traceId}, #{record.eventType}, #{record.schemaVersion},
           #{record.payload, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
           #{record.state}, #{record.attempts}, #{record.createdAt})
        ON DUPLICATE KEY UPDATE event_id = integration_outbox.event_id
        """)
    int insertIfAbsent(@Param("record") IntegrationOutboxRecord record);
    /**
     * Requeue a terminal integration event when its source aggregate retries
     * the same idempotent publication. This keeps a prior DEAD row from
     * turning a durable write into a false success.
     */
    @Update("""
        UPDATE integration_outbox
        SET state = 'PENDING',
            attempts = 0,
            last_error = NULL,
            next_retry_at = NOW(3)
        WHERE event_id = #{eventId}
          AND state = 'DEAD'
        """)
    int requeueDead(@Param("eventId") String eventId);

    /**
     * Requeue rows whose integration dispatcher lease expired before Redis XADD
     * completed. This prevents a crash between claim and publish from
     * stranding the event in {@code CLAIMED} forever.
     */
    @Update("""
        UPDATE integration_outbox
        SET state = 'PENDING',
            claimed_at = NULL,
            claim_owner = NULL,
            next_retry_at = NOW(3),
            last_error = 'Reclaimed stale CLAIMED integration outbox row'
        WHERE state = 'CLAIMED'
          AND (claimed_at IS NULL
               OR claimed_at < DATE_SUB(NOW(3), INTERVAL 60 SECOND))
        """)
    int reclaimStaleClaimed();

    /**
     * Atomically claim up to {@code limit} PENDING rows by setting state=CLAIMED
     * and recording the dispatcher lease owner.
     */
    @Update("""
        UPDATE integration_outbox SET
          state = 'CLAIMED',
          claimed_at = NOW(3),
          claim_owner = #{claimOwner}
        WHERE state = 'PENDING'
          AND next_retry_at <= NOW(3)
          AND event_id IN (
            SELECT event_id FROM (
              SELECT event_id FROM integration_outbox
              WHERE state = 'PENDING' AND next_retry_at <= NOW(3)
              ORDER BY created_at
              LIMIT #{limit}
            ) AS claimable
          )
        """)
    int claimPending(@Param("claimOwner") String claimOwner, @Param("limit") int limit);

    @Select("""
        SELECT * FROM integration_outbox
        WHERE state = 'CLAIMED'
          AND claim_owner = #{claimOwner}
        ORDER BY created_at
        """)
    List<IntegrationOutboxRecord> selectClaimed(@Param("claimOwner") String claimOwner);

    /**
     * Mark a row as DELIVERED after successful Redis Streams XADD, but only
     * while this dispatcher still owns the claim.
     */
    @Update("""
        UPDATE integration_outbox SET
          state = 'DELIVERED',
          claimed_at = NULL,
          claim_owner = NULL,
          delivered_at = NOW(3),
          stream_id = #{streamId},
          last_error = NULL
        WHERE event_id = #{eventId}
          AND state = 'CLAIMED'
          AND claim_owner = #{claimOwner}
        """)
    int markDelivered(@Param("eventId") String eventId,
                      @Param("claimOwner") String claimOwner,
                      @Param("streamId") String streamId);

    /**
     * Mark a row as failed only while this dispatcher still owns the claim.
     * If another dispatcher reclaimed it, the late failure is ignored.
     */
    @Update("""
        UPDATE integration_outbox SET
          state = CASE WHEN attempts + 1 >= #{maxAttempts} THEN 'DEAD' ELSE 'PENDING' END,
          attempts = attempts + 1,
          last_error = #{error},
          claimed_at = NULL,
          claim_owner = NULL,
          next_retry_at = DATE_ADD(NOW(3), INTERVAL POWER(2, attempts + 1) SECOND)
        WHERE event_id = #{eventId}
          AND state = 'CLAIMED'
          AND claim_owner = #{claimOwner}
        """)
    int markFailed(@Param("eventId") String eventId,
                   @Param("claimOwner") String claimOwner,
                   @Param("error") String error,
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
