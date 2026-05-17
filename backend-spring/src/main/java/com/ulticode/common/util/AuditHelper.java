package com.ulticode.common.util;

import com.ulticode.modules.admin.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * Helper component for creating audit log entries.
 * Simplifies the call to {@link AuditService#log} by filling in
 * performer ID, IP address, and user agent automatically.
 */
@Component
@RequiredArgsConstructor
public class AuditHelper {

    private final AuditService auditService;

    /**
     * Log an audit event with the current authenticated user as performer.
     *
     * @param action     audit action constant from {@link AuditActionUtil}
     * @param entityType entity type constant from {@link AuditActionUtil}
     * @param entityId   identifier of the affected entity
     * @param oldValues  previous state (may be null)
     * @param newValues  new state (may be null)
     */
    public void log(String action, String entityType, String entityId,
                    Map<String, Object> oldValues, Map<String, Object> newValues) {
        String performerId = SecurityUtil.getCurrentUserId();
        if (performerId == null) {
            performerId = "system";
        }

        auditService.log(
            performerId,
            null,
            action,
            entityType,
            entityId != null ? entityId : "N/A",
            oldValues,
            newValues,
            getClientIp(),
            getUserAgent()
        );
    }

    /**
     * Log an audit event where the action is performed on a target user.
     *
     * @param action     audit action constant
     * @param entityType entity type constant
     * @param entityId   identifier of the affected entity
     * @param userId     target user ID (the user the action affects)
     * @param oldValues  previous state (may be null)
     * @param newValues  new state (may be null)
     */
    public void logForUser(String action, String entityType, String entityId, String userId,
                           Map<String, Object> oldValues, Map<String, Object> newValues) {
        String performerId = SecurityUtil.getCurrentUserId();
        if (performerId == null) {
            performerId = "system";
        }

        auditService.log(
            performerId,
            userId,
            action,
            entityType,
            entityId != null ? entityId : "N/A",
            oldValues,
            newValues,
            getClientIp(),
            getUserAgent()
        );
    }

    private String getClientIp() {
        ServletRequestAttributes attributes =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }

        HttpServletRequest request = attributes.getRequest();

        String ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.contains(",") ? ip.split(",")[0].trim() : ip;
        }

        ip = request.getRemoteAddr();
        return ip != null ? ip : "unknown";
    }

    private String getUserAgent() {
        ServletRequestAttributes attributes =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }

        HttpServletRequest request = attributes.getRequest();
        String ua = request.getHeader("User-Agent");
        return ua != null && !ua.isEmpty() ? ua : null;
    }
}
