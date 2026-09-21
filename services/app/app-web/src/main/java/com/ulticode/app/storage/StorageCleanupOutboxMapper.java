package com.ulticode.app.storage;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** Claim/retry state transitions for the App storage cleanup outbox. */
@Mapper
public interface StorageCleanupOutboxMapper extends BaseMapper<StorageCleanupOutboxRecord> {

    @Update("""
        UPDATE storage_cleanup_outbox
        SET state = 'CLAIMED', claimed_at = NOW(3), claim_owner = #{claimOwner}
        WHERE state = 'PENDING' AND next_retry_at <= NOW(3)
          AND id IN (
            SELECT id FROM (
              SELECT id FROM storage_cleanup_outbox
              WHERE state = 'PENDING' AND next_retry_at <= NOW(3)
              ORDER BY created_at, id
              LIMIT #{limit}
            ) AS claimable
          )
        """)
    int claimPending(@Param("claimOwner") String claimOwner, @Param("limit") int limit);

    @Select("""
        SELECT * FROM storage_cleanup_outbox
        WHERE state = 'CLAIMED' AND claim_owner = #{claimOwner}
        ORDER BY created_at, id
        """)
    List<StorageCleanupOutboxRecord> selectClaimed(@Param("claimOwner") String claimOwner);

    @Update("""
        UPDATE storage_cleanup_outbox
        SET state = 'PENDING', claimed_at = NULL, claim_owner = NULL,
            next_retry_at = NOW(3), last_error = 'Reclaimed stale storage cleanup row'
        WHERE state = 'CLAIMED'
          AND (claimed_at IS NULL OR claimed_at < DATE_SUB(NOW(3), INTERVAL 60 SECOND))
        """)
    int reclaimStaleClaimed();

    @Update("""
        UPDATE storage_cleanup_outbox
        SET state = 'DELIVERED', delivered_at = NOW(3),
            claimed_at = NULL, claim_owner = NULL, last_error = NULL
        WHERE id = #{id} AND state = 'CLAIMED' AND claim_owner = #{claimOwner}
        """)
    int markDelivered(@Param("id") String id, @Param("claimOwner") String claimOwner);

    @Update("""
        UPDATE storage_cleanup_outbox
        SET state = 'PENDING', attempts = attempts + 1, last_error = #{error},
            claimed_at = NULL, claim_owner = NULL,
            next_retry_at = DATE_ADD(NOW(3), INTERVAL #{backoffSeconds} SECOND)
        WHERE id = #{id} AND state = 'CLAIMED' AND claim_owner = #{claimOwner}
        """)
    int markRetry(@Param("id") String id,
                  @Param("claimOwner") String claimOwner,
                  @Param("error") String error,
                  @Param("backoffSeconds") int backoffSeconds);

    /**
     * Deleting an object is idempotent, so an attempt cap would be the only way
     * a cleanup intent could be stranded: an outage longer than the retry window
     * would leave the avatar in the bucket forever. Rows parked in {@code DEAD}
     * by an earlier release are requeued on the next cycle.
     */
    @Update("""
        UPDATE storage_cleanup_outbox
        SET state = 'PENDING', attempts = 0, last_error = 'Requeued dead cleanup intent',
            claimed_at = NULL, claim_owner = NULL, next_retry_at = NOW(3)
        WHERE state = 'DEAD'
        """)
    int requeueDead();

    @Update("""
        UPDATE storage_cleanup_outbox
        SET state = 'PENDING', attempts = 0, last_error = 'Requeued after terminal row',
            claimed_at = NULL, claim_owner = NULL, delivered_at = NULL,
            next_retry_at = NOW(3)
        WHERE object_key = #{objectKey} AND state IN ('DELIVERED', 'DEAD')
        """)
    int reopenTerminalRow(@Param("objectKey") String objectKey);
}
