package com.ulticode.modules.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.admin.entity.AuditLog;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    @Select("<script>"
        + "SELECT entity_type as entityType, COUNT(*) as count "
        + "FROM audit_logs "
        + "<where>"
        + "  <if test='startDate != null'> AND created_at &gt;= #{startDate}</if>"
        + "  <if test='endDate != null'> AND created_at &lt;= #{endDate}</if>"
        + "  <if test='performerId != null'> AND performer_id = #{performerId}</if>"
        + "</where>"
        + "GROUP BY entity_type ORDER BY count DESC LIMIT 10"
        + "</script>")
    List<Map<String, Object>> selectStatsByEntityType(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        @Param("performerId") String performerId);

    @Select("<script>"
        + "SELECT performer_id as performerId, COUNT(*) as count "
        + "FROM audit_logs "
        + "<where>"
        + "  <if test='startDate != null'> AND created_at &gt;= #{startDate}</if>"
        + "  <if test='endDate != null'> AND created_at &lt;= #{endDate}</if>"
        + "  <if test='performerId != null'> AND performer_id = #{performerId}</if>"
        + "</where>"
        + "GROUP BY performer_id ORDER BY count DESC LIMIT 10"
        + "</script>")
    List<Map<String, Object>> selectStatsByPerformer(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        @Param("performerId") String performerId);
}