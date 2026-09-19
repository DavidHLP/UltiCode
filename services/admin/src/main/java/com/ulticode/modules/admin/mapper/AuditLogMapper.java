package com.ulticode.modules.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.admin.entity.AuditLog;
import com.ulticode.modules.admin.projection.AuditLogQuery;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    /**
     * Insert an incoming owner audit event once. The event id is also the
     * Admin audit-log id, so retries after an inbox lease loss are harmless.
     */
    @Insert("""
        INSERT INTO audit_logs
          (id, performer_id, user_id, action, entity_type, entity_id,
           old_values, new_values, ip_address, user_agent, created_at)
        VALUES
          (#{record.id}, #{record.performerId}, #{record.userId}, #{record.action},
           #{record.entityType}, #{record.entityId},
           #{record.oldValues, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
           #{record.newValues, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
           #{record.ipAddress}, #{record.userAgent}, #{record.createdAt})
        ON DUPLICATE KEY UPDATE id = id
        """)
    int insertIfAbsent(@Param("record") AuditLog record);

    @ConstructorArgs({
        @Arg(column = "entityType", javaType = String.class),
        @Arg(column = "count", javaType = Long.class)
    })
    @SelectProvider(type = AuditLogSqlProvider.class, method = "selectStatsByEntityType")
    List<EntityTypeCount> selectStatsByEntityType(@Param("query") AuditLogQuery query);

    @ConstructorArgs({
        @Arg(column = "performerId", javaType = String.class),
        @Arg(column = "count", javaType = Long.class)
    })
    @SelectProvider(type = AuditLogSqlProvider.class, method = "selectStatsByPerformer")
    List<PerformerCount> selectStatsByPerformer(@Param("query") AuditLogQuery query);

    @ConstructorArgs({
        @Arg(column = "actionType", javaType = String.class),
        @Arg(column = "count", javaType = Long.class)
    })
    @SelectProvider(type = AuditLogSqlProvider.class, method = "selectStatsByActionType")
    List<ActionTypeCount> selectStatsByActionType(@Param("query") AuditLogQuery query);

    @Select("SELECT DATE(created_at) AS date, COUNT(DISTINCT performer_id) AS count "
        + "FROM audit_logs "
        + "WHERE created_at >= #{startDate} AND created_at < #{endDate} "
        + "GROUP BY DATE(created_at) ORDER BY date")
    List<Map<String, Object>> countDailyActiveUsers(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    record EntityTypeCount(String entityType, Long count) {
    }

    record PerformerCount(String performerId, Long count) {
    }

    record ActionTypeCount(String actionType, Long count) {
    }
}
