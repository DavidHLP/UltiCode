package com.ulticode.modules.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.admin.entity.AuditLog;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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

    @Select("<script>"
        + "SELECT entity_type as entityType, COUNT(*) as count "
        + "FROM audit_logs "
        + "<where>"
        + "  <if test='startDate != null'> AND created_at &gt;= #{startDate}</if>"
        + "  <if test='endDate != null'> AND created_at &lt; #{endDate}</if>"
        + "  <if test='performerId != null'> AND performer_id = #{performerId}</if>"
        + "  <if test='userId != null'> AND user_id = #{userId}</if>"
        + "  <if test='entityType != null'> AND entity_type = #{entityType}</if>"
        + "  <if test='action != null'> AND action = #{action}</if>"
        + "  <if test='search != null and !search.isEmpty()'> AND (action LIKE CONCAT('%',#{search},'%') OR entity_type LIKE CONCAT('%',#{search},'%') OR entity_id LIKE CONCAT('%',#{search},'%'))</if>"
        + "</where>"
        + "GROUP BY entity_type ORDER BY count DESC LIMIT 10"
        + "</script>")
    List<Map<String, Object>> selectStatsByEntityType(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        @Param("performerId") String performerId,
        @Param("userId") String userId,
        @Param("entityType") String entityType,
        @Param("action") String action,
        @Param("search") String search);

    @Select("<script>"
        + "SELECT performer_id as performerId, COUNT(*) as count "
        + "FROM audit_logs "
        + "<where>"
        + "  <if test='startDate != null'> AND created_at &gt;= #{startDate}</if>"
        + "  <if test='endDate != null'> AND created_at &lt; #{endDate}</if>"
        + "  <if test='performerId != null'> AND performer_id = #{performerId}</if>"
        + "  <if test='userId != null'> AND user_id = #{userId}</if>"
        + "  <if test='entityType != null'> AND entity_type = #{entityType}</if>"
        + "  <if test='action != null'> AND action = #{action}</if>"
        + "  <if test='search != null and !search.isEmpty()'> AND (action LIKE CONCAT('%',#{search},'%') OR entity_type LIKE CONCAT('%',#{search},'%') OR entity_id LIKE CONCAT('%',#{search},'%'))</if>"
        + "</where>"
        + "GROUP BY performer_id ORDER BY count DESC LIMIT 10"
        + "</script>")
    List<Map<String, Object>> selectStatsByPerformer(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        @Param("performerId") String performerId,
        @Param("userId") String userId,
        @Param("entityType") String entityType,
        @Param("action") String action,
        @Param("search") String search);

    @Select("<script>"
        // UN* prefixes MUST precede their base prefix (e.g. UNFLAG before FLAG),
        // otherwise LIKE 'FLAG%' would also match 'UNFLAG'.
        + "SELECT "
        + "  CASE "
        + "    WHEN action LIKE 'CREATE%' THEN 'CREATE' "
        + "    WHEN action LIKE 'UPDATE%' THEN 'UPDATE' "
        + "    WHEN action LIKE 'DELETE%' THEN 'DELETE' "
        + "    WHEN action LIKE 'UNFLAG%' THEN 'UNFLAG' "
        + "    WHEN action LIKE 'FLAG%' THEN 'FLAG' "
        + "    WHEN action LIKE 'UNBAN%' THEN 'UNBAN' "
        + "    WHEN action LIKE 'BAN%' THEN 'BAN' "
        + "    WHEN action LIKE 'GRANT%' THEN 'GRANT' "
        + "    WHEN action LIKE 'REVOKE%' THEN 'REVOKE' "
        + "    WHEN action LIKE 'RESET%' THEN 'RESET' "
        + "    WHEN action LIKE 'UNPIN%' THEN 'UNPIN' "
        + "    WHEN action LIKE 'PIN%' THEN 'PIN' "
        + "    WHEN action LIKE 'UNLOCK%' THEN 'UNLOCK' "
        + "    WHEN action LIKE 'LOCK%' THEN 'LOCK' "
        + "    WHEN action LIKE 'REQUEUE%' THEN 'REQUEUE' "
        + "    WHEN action LIKE 'MODERATE%' THEN 'MODERATE' "
        + "    ELSE 'OTHER' "
        + "  END AS actionType, "
        + "  COUNT(*) AS count "
        + "FROM audit_logs "
        + "<where>"
        + "  <if test='startDate != null'> AND created_at &gt;= #{startDate}</if>"
        + "  <if test='endDate != null'> AND created_at &lt; #{endDate}</if>"
        + "  <if test='performerId != null'> AND performer_id = #{performerId}</if>"
        + "  <if test='userId != null'> AND user_id = #{userId}</if>"
        + "  <if test='entityType != null'> AND entity_type = #{entityType}</if>"
        + "  <if test='action != null'> AND action = #{action}</if>"
        + "  <if test='search != null and !search.isEmpty()'> AND (action LIKE CONCAT('%',#{search},'%') OR entity_type LIKE CONCAT('%',#{search},'%') OR entity_id LIKE CONCAT('%',#{search},'%'))</if>"
        + "</where>"
        + "GROUP BY actionType ORDER BY count DESC"
        + "</script>")
    List<Map<String, Object>> selectStatsByActionType(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        @Param("performerId") String performerId,
        @Param("userId") String userId,
        @Param("entityType") String entityType,
        @Param("action") String action,
        @Param("search") String search);

    @Select("SELECT DATE(created_at) AS date, COUNT(DISTINCT performer_id) AS count "
        + "FROM audit_logs "
        + "WHERE created_at >= #{startDate} AND created_at < #{endDate} "
        + "GROUP BY DATE(created_at) ORDER BY date")
    List<Map<String, Object>> countDailyActiveUsers(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
}
