package com.ulticode.submission.idempotency.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.submission.idempotency.entity.SubmissionCommandReceiptEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Atomic claim/replay storage for Submission administration commands. */
@Mapper
public interface SubmissionCommandReceiptMapper extends BaseMapper<SubmissionCommandReceiptEntity> {

    @Select("""
        SELECT * FROM submission_command_receipt
        WHERE service = #{service}
          AND operation = #{operation}
          AND idempotency_key = #{idempotencyKey}
        LIMIT 1
        """)
    SubmissionCommandReceiptEntity findByReceiptKey(
            @Param("service") String service,
            @Param("operation") String operation,
            @Param("idempotencyKey") String idempotencyKey);

    @Insert("""
        INSERT IGNORE INTO submission_command_receipt
            (id, command_id, service, operation, idempotency_key,
             request_fingerprint, status, actor_type, actor_id, trace_id, created_at)
        VALUES
            (#{id}, #{commandId}, #{service}, #{operation}, #{idempotencyKey},
             #{requestFingerprint}, 'PROCESSING', #{actorType}, #{actorId},
             #{traceId}, #{createdAt})
        """)
    int insertClaim(SubmissionCommandReceiptEntity receipt);

    @Update("""
        UPDATE submission_command_receipt
        SET status = 'SUCCESS', result_payload = #{resultPayload}
        WHERE id = #{id} AND status = 'PROCESSING'
        """)
    int markSuccess(@Param("id") String id, @Param("resultPayload") String resultPayload);

    @Delete("""
        DELETE FROM submission_command_receipt
        WHERE id = #{id} AND status = 'PROCESSING'
        """)
    int deleteClaim(@Param("id") String id);
}
