package com.ulticode.modules.admin.audit;

import java.util.Map;

/** Typed payload accepted at the Admin audit-event boundary. */
public record AdminAuditRecordedPayload(
        String auditId,
        String performerId,
        String userId,
        String action,
        String entityType,
        String entityId,
        Map<String, Object> oldValues,
        Map<String, Object> newValues,
        String ipAddress,
        String userAgent,
        String createdAt) {
}
