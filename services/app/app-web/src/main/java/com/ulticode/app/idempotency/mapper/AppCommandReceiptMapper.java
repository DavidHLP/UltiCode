package com.ulticode.app.idempotency.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.app.idempotency.entity.AppCommandReceiptEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Mapper for {@link AppCommandReceiptEntity}.
 *
 * <p>Lookup is keyed by the unique triple {@code (service, operation, idempotency_key)}.
 * The provider claims that unique key before invoking the owner mutation, so
 * concurrent retries cannot both execute the side effect.
 */
@Mapper
public interface AppCommandReceiptMapper extends BaseMapper<AppCommandReceiptEntity> {

    @Select("""
        SELECT * FROM app_command_receipt
        WHERE service = #{service}
          AND operation = #{operation}
          AND idempotency_key = #{idempotencyKey}
        LIMIT 1
    """)
    AppCommandReceiptEntity findByReceiptKey(
            @Param("service") String service,
            @Param("operation") String operation,
            @Param("idempotencyKey") String idempotencyKey
    );
    @Insert("""
        INSERT IGNORE INTO app_command_receipt
            (id, command_id, service, operation, idempotency_key,
             request_fingerprint, status, actor_type, actor_id, trace_id, created_at)
        VALUES
            (#{id}, #{commandId}, #{service}, #{operation}, #{idempotencyKey},
             #{requestFingerprint}, 'PROCESSING', #{actorType}, #{actorId},
             #{traceId}, #{createdAt})
    """)
    int insertClaim(AppCommandReceiptEntity receipt);

    @Update("""
        UPDATE app_command_receipt
        SET status = 'SUCCESS', result_payload = #{resultPayload}
        WHERE id = #{id} AND status = 'PROCESSING'
    """)
    int markSuccess(
            @Param("id") String id,
            @Param("resultPayload") String resultPayload
    );

    @Delete("""
        DELETE FROM app_command_receipt
        WHERE id = #{id} AND status = 'PROCESSING'
    """)
    int deleteClaim(@Param("id") String id);
}
