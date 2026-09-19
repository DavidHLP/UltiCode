package com.ulticode.modules.admin.mapper;

import com.ulticode.modules.admin.projection.AuditLogQuery;

import java.util.Map;
import java.util.Objects;

/** Builds the shared, parameterized aggregate SQL for the audit read module. */
public final class AuditLogSqlProvider {

    public String selectStatsByEntityType(Map<String, Object> parameters) {
        return select(
                "entity_type AS entityType, COUNT(*) AS count",
                "entity_type",
                "ORDER BY count DESC LIMIT 10",
                parameters);
    }

    public String selectStatsByPerformer(Map<String, Object> parameters) {
        return select(
                "performer_id AS performerId, COUNT(*) AS count",
                "performer_id",
                "ORDER BY count DESC LIMIT 10",
                parameters);
    }

    public String selectStatsByActionType(Map<String, Object> parameters) {
        return select(
                "CASE "
                        + "WHEN action LIKE 'CREATE%' THEN 'CREATE' "
                        + "WHEN action LIKE 'UPDATE%' THEN 'UPDATE' "
                        + "WHEN action LIKE 'DELETE%' THEN 'DELETE' "
                        + "WHEN action LIKE 'UNFLAG%' THEN 'UNFLAG' "
                        + "WHEN action LIKE 'FLAG%' THEN 'FLAG' "
                        + "WHEN action LIKE 'UNBAN%' THEN 'UNBAN' "
                        + "WHEN action LIKE 'BAN%' THEN 'BAN' "
                        + "WHEN action LIKE 'GRANT%' THEN 'GRANT' "
                        + "WHEN action LIKE 'REVOKE%' THEN 'REVOKE' "
                        + "WHEN action LIKE 'RESET%' THEN 'RESET' "
                        + "WHEN action LIKE 'UNPIN%' THEN 'UNPIN' "
                        + "WHEN action LIKE 'PIN%' THEN 'PIN' "
                        + "WHEN action LIKE 'UNLOCK%' THEN 'UNLOCK' "
                        + "WHEN action LIKE 'LOCK%' THEN 'LOCK' "
                        + "WHEN action LIKE 'REQUEUE%' THEN 'REQUEUE' "
                        + "WHEN action LIKE 'MODERATE%' THEN 'MODERATE' "
                        + "ELSE 'OTHER' END AS actionType, COUNT(*) AS count",
                "actionType",
                "ORDER BY count DESC",
                parameters);
    }

    private String select(
            String columns,
            String groupBy,
            String ordering,
            Map<String, Object> parameters) {
        return "SELECT " + columns + " FROM audit_logs "
                + where(parameters)
                + "GROUP BY " + groupBy + " " + ordering;
    }

    private String where(Map<String, Object> parameters) {
        AuditLogQuery query = (AuditLogQuery) Objects.requireNonNull(
                parameters.get("query"), "query");
        StringBuilder sql = new StringBuilder("WHERE 1 = 1 ");
        if (query.getStartDate() != null) {
            sql.append("AND created_at >= #{query.startDate} ");
        }
        if (query.getEndDate() != null) {
            sql.append("AND created_at < #{query.endDate} ");
        }
        if (query.getPerformerId() != null) {
            sql.append("AND performer_id = #{query.performerId} ");
        }
        if (query.getUserId() != null) {
            sql.append("AND user_id = #{query.userId} ");
        }
        if (query.getEntityType() != null) {
            sql.append("AND entity_type = #{query.entityType} ");
        }
        if (query.getEntityId() != null) {
            sql.append("AND entity_id = #{query.entityId} ");
        }
        if (query.getAction() != null) {
            sql.append("AND action = #{query.action} ");
        }
        if (query.hasSearch()) {
            sql.append("AND (action LIKE CONCAT('%', #{query.search}, '%') ")
                    .append("OR entity_type LIKE CONCAT('%', #{query.search}, '%') ")
                    .append("OR entity_id LIKE CONCAT('%', #{query.search}, '%')) ");
        }
        return sql.toString();
    }
}
