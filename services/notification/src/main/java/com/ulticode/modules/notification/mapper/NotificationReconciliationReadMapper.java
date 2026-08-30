package com.ulticode.modules.notification.mapper;

import com.ulticode.notification.api.dto.NotificationUserReferenceCountDTO;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** Bounded owner-local Notification facts for reconciliation. */
@Mapper
public interface NotificationReconciliationReadMapper {

    @ConstructorArgs({
            @Arg(column = "account_id", javaType = String.class),
            @Arg(column = "row_count", javaType = long.class)
    })
    @Select("""
        SELECT user_id AS account_id, COUNT(*) AS row_count
        FROM notifications
        WHERE user_id IS NOT NULL
          AND user_id > #{afterAccountId}
          AND (#{createdSince,jdbcType=TIMESTAMP} IS NULL
               OR created_at >= #{createdSince,jdbcType=TIMESTAMP})
        GROUP BY user_id
        ORDER BY user_id
        LIMIT #{limit}
        """)
    List<NotificationUserReferenceCountDTO> findUserReferenceCounts(
            @Param("afterAccountId") String afterAccountId,
            @Param("createdSince") LocalDateTime createdSince,
            @Param("limit") int limit);
}
