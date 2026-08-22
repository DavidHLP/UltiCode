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
     * Select pending candidates. The state transition below is the concurrency claim.
     */
    @Select("SELECT * FROM audit_outbox WHERE state = 'PENDING' ORDER BY created_at LIMIT #{batchSize}")
    List<AuditOutboxRecord> claimPending(@Param("batchSize") int batchSize);

    /**
     * Atomically claim one candidate so only one dispatcher processes it.
     * Sets lease timestamp + owner for crash recovery and fencing.
     */
    @Update("UPDATE audit_outbox SET state = 'PROCESSING', claimed_at = NOW(3), claim_owner = #{claimOwner} WHERE id = #{id} AND state = 'PENDING'")
    int claim(@Param("id") String id, @Param("claimOwner") String claimOwner);

    /**
     * Reclaim PROCESSING rows whose lease expired (or was never set).
     * Prevents a crash between claim() and processRecordInNewTx from stranding rows forever.
     */
    @Update("""
        UPDATE audit_outbox
        SET state = 'PENDING',
            claimed_at = NULL,
            claim_owner = NULL
        WHERE state = 'PROCESSING'
          AND (claimed_at IS NULL OR claimed_at < DATE_SUB(NOW(3), INTERVAL 300 SECOND))
        """)
    int reclaimStaleClaimed();

    /**
     * Mark an outbox row as processed only by the dispatcher that owns the claim.
     * Fences late workers whose lease was reclaimed.
     */
    @Update("UPDATE audit_outbox SET state = 'PROCESSED', processed_at = NOW(3) WHERE id = #{id} AND state = 'PROCESSING' AND claim_owner = #{claimOwner}")
    int markProcessed(@Param("id") String id, @Param("claimOwner") String claimOwner);

    /**
     * Mark an outbox row as failed only while it still owns the claim.
     */
    @Update("UPDATE audit_outbox SET state = 'FAILED', processed_at = NOW(3) WHERE id = #{id} AND state = 'PROCESSING' AND claim_owner = #{claimOwner}")
    int markFailed(@Param("id") String id, @Param("claimOwner") String claimOwner);
}
