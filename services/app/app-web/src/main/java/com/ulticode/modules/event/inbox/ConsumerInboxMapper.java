package com.ulticode.modules.event.inbox;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MyBatis-Plus mapper for {@link ConsumerInboxRecord} (P6-INBOX-001).
 */
@Mapper
public interface ConsumerInboxMapper extends BaseMapper<ConsumerInboxRecord> {

    /**
     * Insert an inbox row unless the same consumer has already seen the event.
     *
     * <p>The unique {@code (consumer, event_id)} key is the idempotency seam
     * between Redis Streams and the durable worker. A zero return value means
     * that the event was already staged and can be acknowledged in Redis.
     */
    @Insert("""
        INSERT INTO consumer_inbox
          (id, consumer, event_id, event_type, payload, state, attempts, next_retry_at)
        VALUES
          (#{id}, #{consumer}, #{eventId}, #{eventType}, #{payload}, 'PENDING', 0, NOW(3))
        ON DUPLICATE KEY UPDATE id = id
        """)
    int insertIfAbsent(@Param("id") String id,
                       @Param("consumer") String consumer,
                       @Param("eventId") String eventId,
                       @Param("eventType") String eventType,
                       @Param("payload") String payload);

    /**
     * Lease up to {@code limit} PENDING/stale-PROCESSING rows for a consumer.
     * Sets state=PROCESSING, lease_owner, lease_expires_at=NOW+30s.
     */
    @Update("""
        UPDATE consumer_inbox SET
          state = 'PROCESSING',
          lease_owner = #{leaseOwner},
          lease_expires_at = DATE_ADD(NOW(3), INTERVAL 30 SECOND)
        WHERE consumer = #{consumer}
          AND ((state = 'PENDING' AND next_retry_at <= NOW(3))
           OR (state = 'PROCESSING' AND lease_expires_at < NOW(3)))
          AND id IN (
            SELECT id FROM (
              SELECT id FROM consumer_inbox
              WHERE consumer = #{consumer}
                AND ((state = 'PENDING' AND next_retry_at <= NOW(3))
                 OR (state = 'PROCESSING' AND lease_expires_at < NOW(3)))
              ORDER BY created_at
              LIMIT #{limit}
            ) AS claimable
          )
        """)
    int claimLease(@Param("leaseOwner") String leaseOwner,
                   @Param("consumer") String consumer, @Param("limit") int limit);

    @Select("""
        SELECT * FROM consumer_inbox
        WHERE consumer = #{consumer}
          AND state = 'PROCESSING' AND lease_owner = #{leaseOwner}
        ORDER BY created_at
        """)
    List<ConsumerInboxRecord> selectLeased(@Param("leaseOwner") String leaseOwner,
                                           @Param("consumer") String consumer);

    @Update("""
        UPDATE consumer_inbox SET
          state = 'PROCESSED',
          processed_at = NOW(3),
          last_error = NULL,
          lease_owner = NULL,
          lease_expires_at = NULL
        WHERE id = #{id}
          AND consumer = #{consumer}
          AND state = 'PROCESSING'
          AND lease_owner = #{leaseOwner}
        """)
    int markProcessed(@Param("id") String id,
                      @Param("consumer") String consumer,
                      @Param("leaseOwner") String leaseOwner);

    @Update("""
        UPDATE consumer_inbox SET
          state = CASE WHEN attempts + 1 >= #{maxAttempts} THEN 'DEAD' ELSE 'PENDING' END,
          attempts = attempts + 1,
          last_error = #{error},
          lease_owner = NULL,
          lease_expires_at = NULL,
          next_retry_at = DATE_ADD(NOW(3), INTERVAL POWER(2, LEAST(attempts + 1, 10)) SECOND)
        WHERE id = #{id}
          AND consumer = #{consumer}
          AND state = 'PROCESSING'
          AND lease_owner = #{leaseOwner}
        """)
    int markFailed(@Param("id") String id,
                   @Param("consumer") String consumer,
                   @Param("leaseOwner") String leaseOwner,
                   @Param("error") String error,
                   @Param("maxAttempts") int maxAttempts);
}
