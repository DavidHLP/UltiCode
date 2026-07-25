package com.ulticode.common.util;

import com.ulticode.common.auth.CurrentUserProvider;
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
    private final ClientIpResolver clientIpResolver;
    private final CurrentUserProvider currentUserProvider;

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
        // Performer id flows through the CurrentUserProvider port — the only
        // seam that should read the security context.
        String performerId = currentUserProvider.getCurrentUserId();
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
        String performerId = currentUserProvider.getCurrentUserId();
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
        return clientIpResolver.resolveCurrent();
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
