package com.ulticode.audit;

import com.ulticode.common.audit.AuditSinkPort;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.websecurity.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * Shared metadata normalization and emission for audit records.
 *
 * <p>The callers retain ownership of target-user selection and old/new value
 * shaping. {@link #prepare()} exists so an aspect can preserve its existing
 * pre-invocation metadata lookup timing; recorder callers can use
 * {@link #emit(String, String, String, String, Map, Map)} directly.
 */
public final class AuditEmissionPolicy {

    private static final String SYSTEM_PERFORMER = "system";
    private static final String UNKNOWN_ENTITY = "N/A";

    private final AuditSinkPort auditSinkPort;
    private final ClientIpResolver clientIpResolver;
    private final CurrentUserProvider currentUserProvider;

    public AuditEmissionPolicy(AuditSinkPort auditSinkPort,
                               ClientIpResolver clientIpResolver,
                               CurrentUserProvider currentUserProvider) {
        this.auditSinkPort = auditSinkPort;
        this.clientIpResolver = clientIpResolver;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Captures normalized performer, IP, and User-Agent metadata.
     *
     * @return metadata snapshot for a later emission
     */
    public Prepared prepare() {
        String performerId = currentUserProvider.getCurrentUserId();
        if (performerId == null) {
            performerId = SYSTEM_PERFORMER;
        }
        return new Prepared(performerId, clientIpResolver.resolveCurrent(), getUserAgent());
    }

    /**
     * Emits an audit event using an existing metadata snapshot.
     *
     * <p>Target-user meaning and old/new value shaping are supplied by the
     * caller and passed through unchanged.
     */
    public void emit(Prepared prepared,
                     String userId,
                     String action,
                     String entityType,
                     String entityId,
                     Map<String, Object> oldValues,
                     Map<String, Object> newValues) {
        auditSinkPort.log(
            prepared.performerId(),
            userId,
            action,
            entityType,
            normalizeEntityId(entityId),
            oldValues,
            newValues,
            prepared.ipAddress(),
            prepared.userAgent()
        );
    }

    /**
     * Captures metadata and emits one audit event.
     */
    public void emit(String userId,
                     String action,
                     String entityType,
                     String entityId,
                     Map<String, Object> oldValues,
                     Map<String, Object> newValues) {
        emit(prepare(), userId, action, entityType, entityId, oldValues, newValues);
    }

    private static String normalizeEntityId(String entityId) {
        return entityId == null || entityId.isEmpty() ? UNKNOWN_ENTITY : entityId;
    }

    private static String getUserAgent() {
        ServletRequestAttributes attributes =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String userAgent = request.getHeader("User-Agent");
        return userAgent != null && !userAgent.isEmpty() ? userAgent : null;
    }

    /** Metadata captured before an audited invocation proceeds. */
    public record Prepared(String performerId, String ipAddress, String userAgent) {
    }
}
