package com.ulticode.common.aspect;

import com.ulticode.common.annotation.Audited;
import com.ulticode.common.audit.AuditSinkPort;
import com.ulticode.common.util.AuditContext;
import com.ulticode.websecurity.util.ClientIpResolver;
import com.ulticode.common.auth.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.CodeSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * Audit logging aspect that intercepts methods annotated with {@link Audited}.
 *
 * <p>Automatically captures: performer ID, client IP, user agent, action, entity type.
 * For old/new value capture, use {@link AuditContext} inside the method body.
 *
 * <p><strong>Cross-cutting seam:</strong> the aspect depends only on
 * {@link AuditSinkPort} — the admin module ships the production
 * adapter. The aspect no longer imports {@code AuditService} directly.
 * See {@code /tmp/architecture-review-1783485814.html} candidate 4.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditSinkPort auditSinkPort;
    private final ClientIpResolver clientIpResolver;
    private final CurrentUserProvider currentUserProvider;

    @Around("@annotation(audited)")
    public Object auditAround(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        String performerId = currentUserProvider.getCurrentUserId();
        if (performerId == null) {
            performerId = "system";
        }

        String ip = clientIpResolver.resolveCurrent();
        String userAgent = getUserAgent();

        String targetUserId = resolveParamValue(joinPoint, audited.userIdFrom());
        String resolvedEntityId = resolveParamValue(joinPoint, audited.entityIdFrom());

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Exception e) {
            String userId = firstNonNull(targetUserId, AuditContext.getUserId());
            String entityId = firstNonNull(resolvedEntityId, AuditContext.getEntityId(), "N/A");

            auditSinkPort.log(
                performerId,
                userId,
                audited.action(),
                audited.entityType(),
                entityId,
                AuditContext.getOldValues(),
                Map.of("error", e.getClass().getSimpleName(),
                       "message", e.getMessage() != null ? e.getMessage() : ""),
                ip,
                userAgent
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

        auditSinkPort.log(
            performerId,
            userId,
            audited.action(),
            audited.entityType(),
            entityId != null ? entityId : "N/A",
            oldValues,
            newValues,
            ip,
            userAgent
        );

        AuditContext.clear();
        return result;
    }

    private String resolveParamValue(ProceedingJoinPoint joinPoint, String paramName) {
        if (paramName == null || paramName.isEmpty()) {
            return null;
        }

        if (!(joinPoint.getSignature() instanceof CodeSignature signature)) {
            return null;
        }

        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < paramNames.length; i++) {
            if (paramName.equals(paramNames[i]) && args[i] != null) {
                return args[i].toString();
            }
        }

        return null;
    }

    private String extractEntityId(Object result) {
        if (result == null) {
            return "N/A";
        }
        try {
            java.lang.reflect.Method getId = result.getClass().getMethod("getId");
            Object id = getId.invoke(result);
            return id != null ? id.toString() : "N/A";
        } catch (Exception e) {
            return "N/A";
        }
    }

    private Map<String, Object> captureSimpleState(Object result) {
        if (result == null) {
            return null;
        }
        try {
            java.lang.reflect.Method getId = result.getClass().getMethod("getId");
            Object id = getId.invoke(result);
            return Map.of("id", id != null ? id.toString() : "N/A");
        } catch (Exception e) {
            return null;
        }
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

    private static String firstNonNull(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return null;
    }
}
