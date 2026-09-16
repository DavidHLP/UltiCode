package com.ulticode.common.audit;

import com.ulticode.audit.AuditEmissionPolicy;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.websecurity.util.ClientIpResolver;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Default {@link AuditRecorder} implementation.
 *
 * <p>Shares the metadata-capture contract of the {@code @Audited} aspect
 * through {@link AuditEmissionPolicy} and forwards through {@link AuditSinkPort},
 * so callers crossing either seam produce audit rows with identical shape.
 * The deprecated {@code AuditHelper} shim now delegates here.
 *
 * <p>Thread-safe; {@link AuditSinkPort} implementations are responsible for
 * cross-process serialization.
 *
 * @author ulticode
 */
@Component
public class DefaultAuditRecorder implements AuditRecorder {

    private final AuditEmissionPolicy auditEmissionPolicy;

    public DefaultAuditRecorder(AuditSinkPort auditSinkPort,
                                ClientIpResolver clientIpResolver,
                                CurrentUserProvider currentUserProvider) {
        this.auditEmissionPolicy =
            new AuditEmissionPolicy(auditSinkPort, clientIpResolver, currentUserProvider);
    }

    @Override
    public void record(String action,
                       String entityType,
                       String entityId,
                       Map<String, Object> oldValues,
                       Map<String, Object> newValues) {
        auditEmissionPolicy.emit(null, action, entityType, entityId, oldValues, newValues);
    }

    @Override
    public void recordForUser(String action,
                              String entityType,
                              String entityId,
                              String userId,
                              Map<String, Object> oldValues,
                              Map<String, Object> newValues) {
        auditEmissionPolicy.emit(userId, action, entityType, entityId, oldValues, newValues);
    }

}
