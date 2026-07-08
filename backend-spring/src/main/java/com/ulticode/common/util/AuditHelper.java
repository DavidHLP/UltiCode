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
 *
 * @deprecated Use {@link com.ulticode.common.annotation.Audited} annotation on service methods instead.
 *             For cases that don't fit the annotation model (e.g., bulk operations),
 *             this helper can still be used.
 */
@Component
@RequiredArgsConstructor
@Deprecated(forRemoval = false)
public class AuditHelper {

    private final AuditService auditService;

    /**
     * Log an audit event with the current authenticated user as performer.
     *
     * @param action     audit action constant from {@link AuditVocabulary}
     * @param entityType entity type constant from {@link AuditVocabulary}
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

        // Order matters: X-Forwarded-For is the de-facto reverse-proxy/load-balancer
        // header; X-Real-IP is the simplified single-value variant some proxies
        // (notably older nginx configs) prefer. We honour whichever arrives first
        // and isn't the literal "unknown" sentinel, and pick the leftmost address
        // from comma-separated chains (that's the original client).
        // A whitespace-only header value would otherwise pass `!isEmpty()` and
        // be persisted as the empty string into audit_logs.ip_address — trim
        // first, then re-check, so only meaningful values flow downstream.
        String[] forwardedHeaders = {"X-Forwarded-For", "X-Real-IP"};
        for (String header : forwardedHeaders) {
            String value = request.getHeader(header);
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty() && !"unknown".equalsIgnoreCase(trimmed)) {
                    return trimmed.contains(",") ? trimmed.split(",")[0].trim() : trimmed;
                }
            }
        }

        String ip = request.getRemoteAddr();
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
