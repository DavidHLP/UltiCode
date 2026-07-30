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
     * Claim pending outbox records using FOR UPDATE SKIP LOCKED for safe concurrent worker execution.
     */
    @Select("SELECT * FROM audit_outbox WHERE state = 'PENDING' ORDER BY created_at LIMIT #{batchSize} FOR UPDATE SKIP LOCKED")
    List<AuditOutboxRecord> claimPending(@Param("batchSize") int batchSize);

    /**
     * Mark an outbox row as processed.
     */
    @Update("UPDATE audit_outbox SET state = 'PROCESSED', processed_at = NOW() WHERE id = #{id}")
    int markProcessed(@Param("id") String id);

    /**
     * Mark an outbox row as failed.
     */
    @Update("UPDATE audit_outbox SET state = 'FAILED', processed_at = NOW() WHERE id = #{id}")
    int markFailed(@Param("id") String id);
}
