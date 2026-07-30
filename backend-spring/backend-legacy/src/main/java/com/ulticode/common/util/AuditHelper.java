package com.ulticode.common.util;

import com.ulticode.websecurity.util.ClientIpResolver;

import com.ulticode.common.audit.AuditSinkPort;
import com.ulticode.common.auth.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * Helper component for creating audit log entries.
 * Simplifies the call to {@link AuditSinkPort#log} by filling in
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

    private final AuditSinkPort auditSinkPort;
    private final ClientIpResolver clientIpResolver;
    private final CurrentUserProvider currentUserProvider;

    /**
     * Log an audit event with the current authenticated user as performer.
     *
     * @param action     Action name (e.g., "UPDATE_PROBLEM")
     * @param entityType Entity type (e.g., "PROBLEM")
     * @param entityId   Target entity ID
     * @param oldValues  State before mutation (can be null)
     * @param newValues  State after mutation (can be null)
     */
    public void log(String action, String entityType, String entityId,
                    Map<String, Object> oldValues, Map<String, Object> newValues) {
        String performerId = currentUserProvider.getCurrentUserId();
        if (performerId == null) {
            performerId = "system";
        }

        auditSinkPort.log(
            performerId,
            null,
            action,
            entityType,
            entityId,
            oldValues,
            newValues,
            getClientIp(),
            getUserAgent()
        );
    }

    /**
     * Log an audit event where the action is performed on a target user.
     *
     * @param action     Action name (e.g., "BAN_USER")
     * @param entityType Entity type (e.g., "USER")
     * @param entityId   Target entity ID
     * @param userId     Target user ID (for logForUser pattern)
     * @param oldValues  State before mutation (can be null)
     * @param newValues  State after mutation (can be null)
     */
    public void logForUser(String action, String entityType, String entityId, String userId,
                           Map<String, Object> oldValues, Map<String, Object> newValues) {
        String performerId = currentUserProvider.getCurrentUserId();
        if (performerId == null) {
            performerId = "system";
        }

        auditSinkPort.log(
            performerId,
            userId,
            action,
            entityType,
            entityId,
            oldValues,
            newValues,
            getClientIp(),
            getUserAgent()
        );
    }

    private String getClientIp() {
        return clientIpResolver.resolveCurrent();
    }

    private String getUserAgent() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            return request.getHeader("User-Agent");
        }
        return null;
    }
}
