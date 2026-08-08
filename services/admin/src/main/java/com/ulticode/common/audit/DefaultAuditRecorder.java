package com.ulticode.common.audit;

import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.websecurity.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * Default {@link AuditRecorder} implementation.
 *
 * <p>Mirrors the metadata-capture contract of the {@code @Audited} aspect
 * (performer / IP / user-agent) and forwards through {@link AuditSinkPort},
 * so callers crossing either seam produce audit rows with identical shape.
 * The deprecated {@code AuditHelper} shim now delegates here, and the five
 * policy / bulk callers ({@code ForumFlagPolicyImpl},
 * {@code ForumPostFieldToggleImpl}, {@code UserManagementServiceImpl.bulkDelete},
 * the dead injection in {@code AdminContestMutationServiceImpl}, and the
 * {@code AdminForumServiceImpl.deletePost} direct write) cross this seam.
 *
 * <p>Thread-safe; {@link AuditSinkPort} implementations are responsible for
 * cross-process serialization.
 *
 * @author ulticode
 */
@Component
@RequiredArgsConstructor
public class DefaultAuditRecorder implements AuditRecorder {

    private final AuditSinkPort auditSinkPort;
    private final ClientIpResolver clientIpResolver;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public void record(String action,
                       String entityType,
                       String entityId,
                       Map<String, Object> oldValues,
                       Map<String, Object> newValues) {
        String performerId = currentUserProvider.getCurrentUserId();
        if (performerId == null) {
            performerId = "system";
        }
        auditSinkPort.log(
            performerId,
            null,
            action,
            entityType,
            entityId != null ? entityId : "N/A",
            oldValues,
            newValues,
            clientIpResolver.resolveCurrent(),
            getUserAgent()
        );
    }

    @Override
    public void recordForUser(String action,
                              String entityType,
                              String entityId,
                              String userId,
                              Map<String, Object> oldValues,
                              Map<String, Object> newValues) {
        String performerId = currentUserProvider.getCurrentUserId();
        if (performerId == null) {
            performerId = "system";
        }
        auditSinkPort.log(
            performerId,
            userId,
            action,
            entityType,
            entityId != null ? entityId : "N/A",
            oldValues,
            newValues,
            clientIpResolver.resolveCurrent(),
            getUserAgent()
        );
    }

    private static String getUserAgent() {
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
