package com.ulticode.modules.admin.outbox.mapper;

import com.ulticode.modules.admin.outbox.AuditOutboxRecord;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * Mapper for {@link AuditOutboxRecord} (P3-AUDIT-001).
 */
@Mapper
public interface AuditOutboxMapper extends BaseMapper<AuditOutboxRecord> {

    /**
     * Atomically claim a bounded batch of due rows. PENDING rows are new;
     * FAILED rows are retryable while attempts is below the caller-supplied
     * ceiling and next_retry_at is due.
     * A FAILED row with attempts at or above the ceiling is terminal.
     */
    @Update("""
        UPDATE audit_outbox
        SET state = 'PROCESSING', claimed_at = NOW(3), claim_owner = #{claimOwner}
        WHERE (
              (state = 'PENDING' AND next_retry_at <= NOW(3))
           OR (state = 'FAILED' AND attempts < #{maxAttempts} AND next_retry_at <= NOW(3))
          )
          AND id IN (
            SELECT id FROM (
              SELECT id FROM audit_outbox
              WHERE (
                    (state = 'PENDING' AND next_retry_at <= NOW(3))
                 OR (state = 'FAILED' AND attempts < #{maxAttempts} AND next_retry_at <= NOW(3))
                )
              ORDER BY created_at, id
              LIMIT #{limit}
            ) AS claimable
          )
        """)
    int claimPending(@Param("claimOwner") String claimOwner,
                     @Param("limit") int limit,
                     @Param("maxAttempts") int maxAttempts);

    @Select("""
        SELECT * FROM audit_outbox
        WHERE state = 'PROCESSING' AND claim_owner = #{claimOwner}
        ORDER BY created_at, id
        """)
    List<AuditOutboxRecord> selectClaimed(@Param("claimOwner") String claimOwner);

    /**
     * Reclaim expired PROCESSING rows. Rows already at the caller-supplied
     * attempt ceiling remain FAILED (terminal); all other rows become immediately
     * claimable PENDING rows. Attempts is authoritative so legacy FAILED
     * rows added before retry metadata remain retryable with attempts = 0.
     */
    @Update("""
        UPDATE audit_outbox
        SET state = CASE WHEN attempts >= #{maxAttempts} THEN 'FAILED' ELSE 'PENDING' END,
            claimed_at = NULL,
            claim_owner = NULL,
            next_retry_at = CASE WHEN attempts >= #{maxAttempts} THEN next_retry_at ELSE NOW(3) END
        WHERE state = 'PROCESSING'
          AND (claimed_at IS NULL OR claimed_at < DATE_SUB(NOW(3), INTERVAL 300 SECOND))
        """)
    int reclaimStaleClaimed(@Param("maxAttempts") int maxAttempts);

    /**
     * Mark an outbox row as processed only by the dispatcher that owns the claim.
     * Fences late workers whose lease was reclaimed.
     */
    @Update("""
        UPDATE audit_outbox
        SET state = 'PROCESSED',
            processed_at = NOW(3),
            claimed_at = NULL,
            claim_owner = NULL,
            last_error = NULL
        WHERE id = #{id} AND state = 'PROCESSING' AND claim_owner = #{claimOwner}
        """)
    int markProcessed(@Param("id") String id, @Param("claimOwner") String claimOwner);

    /**
     * Record a failed attempt while the caller still owns the claim.
     * FAILED rows remain retryable while attempts is below maxAttempts and become
     * terminal at maxAttempts; attempts and last_error provide the evidence
     * needed to distinguish those two meanings without adding a new state word.
     * The dispatcher contract uses a 30-second retry backoff.
     */
    @Update("""
        UPDATE audit_outbox
        SET state = 'FAILED',
            processed_at = CASE
                WHEN attempts + 1 >= #{maxAttempts} THEN NOW(3)
                ELSE NULL
            END,
            attempts = attempts + 1,
            last_error = #{error},
            next_retry_at = DATE_ADD(NOW(3), INTERVAL 30 SECOND),
            claimed_at = NULL,
            claim_owner = NULL
        WHERE id = #{id} AND state = 'PROCESSING' AND claim_owner = #{claimOwner}
        """)
    int markFailedWithRetry(@Param("id") String id,
                            @Param("claimOwner") String claimOwner,
                            @Param("error") String error,
                            @Param("maxAttempts") int maxAttempts);
}
