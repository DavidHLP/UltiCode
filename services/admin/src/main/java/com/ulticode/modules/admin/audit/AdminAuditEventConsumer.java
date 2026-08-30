package com.ulticode.modules.admin.audit;

import com.ulticode.modules.admin.entity.AuditLog;
import com.ulticode.modules.admin.mapper.AuditLogMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Applies owner-local {@code AuditRecorded} events to Admin's audit log.
 *
 * <p>The event id is used as the audit-log primary key. The mapper's
 * idempotent insert therefore makes replay after a lease loss safe.</p>
 */
@Component
@RequiredArgsConstructor
public class AdminAuditEventConsumer {

    private final AuditLogMapper auditLogMapper;

    public void consume(String eventId, AdminAuditRecordedPayload payload) {
        String safeEventId = requiredText(eventId, "eventId", 40);
        if (payload == null) {
            throw new IllegalArgumentException("Audit event payload must be an object");
        }
        String auditId = requiredText(payload.auditId(), "auditId", 40);
        if (!safeEventId.equals(auditId)) {
            throw new IllegalArgumentException("Audit event id does not match payload auditId");
        }

        AuditLog auditLog = new AuditLog();
        auditLog.setId(safeEventId);
        auditLog.setPerformerId(requiredText(payload.performerId(), "performerId", 40));
        auditLog.setUserId(optionalText(payload.userId(), "userId", 40));
        auditLog.setAction(requiredText(payload.action(), "action", 64));
        auditLog.setEntityType(requiredText(payload.entityType(), "entityType", 64));
        auditLog.setEntityId(requiredText(payload.entityId(), "entityId", 64));
        auditLog.setOldValues(optionalMap(payload.oldValues(), "oldValues"));
        auditLog.setNewValues(optionalMap(payload.newValues(), "newValues"));
        auditLog.setIpAddress(optionalText(payload.ipAddress(), "ipAddress", 45, "unknown"));
        auditLog.setUserAgent(optionalText(payload.userAgent(), "userAgent", 255));
        auditLog.setCreatedAt(parseCreatedAt(payload.createdAt()));

        auditLogMapper.insertIfAbsent(auditLog);
    }

    private static LocalDateTime parseCreatedAt(Object value) {
        String createdAt = requiredText(value, "createdAt", 40);
        try {
            return LocalDateTime.parse(createdAt);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid audit event createdAt", e);
        }
    }

    private static String requiredText(Object value, String field, int maxLength) {
        if (!(value instanceof String text) || text.isBlank() || text.length() > maxLength) {
            throw new IllegalArgumentException("Invalid audit event field: " + field);
        }
        return text;
    }

    private static String optionalText(Object value, String field, int maxLength) {
        return optionalText(value, field, maxLength, null);
    }

    private static String optionalText(Object value, String field, int maxLength, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return requiredText(value, field, maxLength);
    }

    private static Map<String, Object> optionalMap(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException("Invalid audit event field: " + field);
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("Invalid audit event map key: " + field);
            }
            copy.put(key, entry.getValue());
        }
        return copy;
    }
}
