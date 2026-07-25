package com.ulticode.modules.admin.port.adapter;

import com.ulticode.common.audit.AuditSinkPort;
import com.ulticode.modules.admin.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Production adapter for {@link AuditSinkPort}. Delegates to the
 * admin module's {@link AuditService} so the audit aspect never imports
 * the admin service directly.
 *
 * <p>Two adapters justify the seam: this class is the production path;
 * an in-memory variant lives in {@code common/audit/} test sources.
 *
 * @author ulticode
 */
@Component
@RequiredArgsConstructor
public class DefaultAuditSinkAdapter implements AuditSinkPort {

    private final AuditService auditService;

    @Override
    public void log(String performerId,
                    String userId,
                    String action,
                    String entityType,
                    String entityId,
                    Map<String, Object> oldValues,
                    Map<String, Object> newValues,
                    String ipAddress,
                    String userAgent) {
        auditService.log(performerId, userId, action, entityType, entityId,
                oldValues, newValues, ipAddress, userAgent);
    }
}
