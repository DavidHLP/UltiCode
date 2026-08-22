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
     */
    @Update("UPDATE audit_outbox SET state = 'PROCESSING' WHERE id = #{id} AND state = 'PENDING'")
    int claim(@Param("id") String id);

    /**
     * Mark an outbox row as processed only by the dispatcher that claimed it.
     */
    @Update("UPDATE audit_outbox SET state = 'PROCESSED', processed_at = NOW() WHERE id = #{id} AND state = 'PROCESSING'")
    int markProcessed(@Param("id") String id);

    /**
     * Mark an outbox row as failed only while it is being processed.
     */
    @Update("UPDATE audit_outbox SET state = 'FAILED', processed_at = NOW() WHERE id = #{id} AND state = 'PROCESSING'")
    int markFailed(@Param("id") String id);
}
