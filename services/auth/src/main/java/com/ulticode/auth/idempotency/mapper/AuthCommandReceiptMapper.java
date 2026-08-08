package com.ulticode.auth.idempotency.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.auth.idempotency.entity.AuthCommandReceiptEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuthCommandReceiptMapper extends BaseMapper<AuthCommandReceiptEntity> {

    @Select("""
        SELECT * FROM auth_command_receipt
        WHERE service = #{service}
          AND operation = #{operation}
          AND idempotency_key = #{idempotencyKey}
        LIMIT 1
    """)
    AuthCommandReceiptEntity findByReceiptKey(
            @Param("service") String service,
            @Param("operation") String operation,
            @Param("idempotencyKey") String idempotencyKey
    );
}
