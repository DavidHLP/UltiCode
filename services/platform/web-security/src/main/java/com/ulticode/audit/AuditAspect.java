package com.ulticode.audit;

import com.ulticode.common.annotation.Audited;
import com.ulticode.common.audit.AuditSinkPort;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.util.AuditContext;
import com.ulticode.websecurity.util.ClientIpResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.CodeSignature;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Audit logging aspect that intercepts methods annotated with {@link Audited}.
 *
 * <p>Automatically captures: performer ID, client IP, user agent, action, entity type.
 * For old/new value capture, use {@link AuditContext} inside the method body.
 *
 * <p><strong>Cross-cutting seam:</strong> emission is centralized in
 * {@link AuditEmissionPolicy}, which receives the existing ports explicitly.
 * The aspect no longer imports {@code AuditService} directly.
 */
@Aspect
@Component
public class AuditAspect {

    private final AuditEmissionPolicy auditEmissionPolicy;

    public AuditAspect(AuditSinkPort auditSinkPort,
                       ClientIpResolver clientIpResolver,
                       CurrentUserProvider currentUserProvider) {
        this.auditEmissionPolicy =
            new AuditEmissionPolicy(auditSinkPort, clientIpResolver, currentUserProvider);
    }

    @Around("@annotation(audited)")
    public Object auditAround(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        AuditEmissionPolicy.Prepared emissionMetadata = auditEmissionPolicy.prepare();

        String targetUserId = resolveParamValue(joinPoint, audited.userIdFrom());
        String resolvedEntityId = resolveParamValue(joinPoint, audited.entityIdFrom());

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Exception e) {
            String userId = firstNonNull(targetUserId, AuditContext.getUserId());
            String entityId = firstNonNull(resolvedEntityId, AuditContext.getEntityId(), "N/A");

            auditEmissionPolicy.emit(
                emissionMetadata,
                userId,
                audited.action(),
                audited.entityType(),
                entityId,
                AuditContext.getOldValues(),
                Map.of("error", e.getClass().getSimpleName(),
                       "message", e.getMessage() != null ? e.getMessage() : "")
            );
            AuditContext.clear();
            throw e;
        }

        // Resolve entity ID: param > AuditContext > reflection on result
        String entityId = firstNonNull(resolvedEntityId, AuditContext.getEntityId());
        if (entityId == null || entityId.isEmpty()) {
            entityId = extractEntityId(result);
        }

        // Resolve userId: annotation param > AuditContext
        String userId = firstNonNull(targetUserId, AuditContext.getUserId());

        // Get old/new values from AuditContext (populated by method body)
        Map<String, Object> oldValues = AuditContext.getOldValues();
        Map<String, Object> newValues = AuditContext.getNewValues();

        // Optionally capture new state from return value if context didn't provide it
        if (newValues == null && audited.captureNewState() && result != null) {
            newValues = captureSimpleState(result);
        }

        auditEmissionPolicy.emit(
            emissionMetadata,
            userId,
            audited.action(),
            audited.entityType(),
            entityId,
            oldValues,
            newValues
        );

        AuditContext.clear();
        return result;
    }

    private String resolveParamValue(ProceedingJoinPoint joinPoint, String paramName) {
        if (paramName == null || paramName.isEmpty()) {
            return null;
        }
        String[] paramNames = ((CodeSignature) joinPoint.getSignature()).getParameterNames();
        Object[] args = joinPoint.getArgs();
        if (paramNames == null) {
            return null;
        }
        for (int i = 0; i < paramNames.length; i++) {
            if (paramNames[i].equals(paramName)) {
                Object arg = args[i];
                return arg != null ? arg.toString() : null;
            }
        }
        return null;
    }

    private String extractEntityId(Object result) {
        if (result == null) {
            return null;
        }
        // Try getId() first
        try {
            Object id = result.getClass().getMethod("getId").invoke(result);
            if (id != null) {
                return id.toString();
            }
        } catch (Exception ignored) {
            // fall through
        }
        // Try id field
        try {
            Object id = result.getClass().getField("id").get(result);
            if (id != null) {
                return id.toString();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }

    private Map<String, Object> captureSimpleState(Object result) {
        if (result == null) {
            return null;
        }
        try {
            return Map.of("result", result.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstNonNull(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return null;
    }
}
