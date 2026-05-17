package com.ulticode.common.aspect;

import com.ulticode.common.annotation.Audited;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.admin.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * Audit logging aspect that intercepts methods annotated with {@link Audited}.
 *
 * <p><strong>Note:</strong> This aspect captures basic action metadata (performer, IP, user agent).
 * For reliable old/new value capture, call {@link AuditService#log} directly in service code.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;

    @Around("@annotation(audited)")
    public Object auditAround(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        String performerId = SecurityUtil.getCurrentUserId();
        if (performerId == null) {
            performerId = "system";
        }

        String ip = getClientIp();
        String userAgent = getUserAgent();

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Exception e) {
            auditService.log(
                performerId,
                null,
                audited.action(),
                audited.entityType(),
                "N/A",
                null,
                Map.of("error", e.getClass().getSimpleName(), "message", e.getMessage()),
                ip,
                userAgent
            );
            throw e;
        }

        Map<String, Object> newValues = null;
        if (audited.captureNewState() && result != null) {
            newValues = captureSimpleState(result);
        }

        auditService.log(
            performerId,
            null,
            audited.action(),
            audited.entityType(),
            extractEntityId(result),
            null,
            newValues,
            ip,
            userAgent
        );

        return result;
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
