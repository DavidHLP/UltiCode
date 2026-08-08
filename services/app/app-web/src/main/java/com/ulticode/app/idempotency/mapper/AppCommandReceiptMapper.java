package com.ulticode.app.idempotency.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.app.idempotency.entity.AppCommandReceiptEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Mapper for {@link AppCommandReceiptEntity}.
 *
 * <p>Lookup is keyed by the unique triple {@code (service, operation, idempotency_key)}.
 * The unique constraint on these columns guarantees at-most-once insert;
 * concurrent same-key requests collide on the second insert, which rolls back
 * the entire transaction (including the profile mutation).
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
}
