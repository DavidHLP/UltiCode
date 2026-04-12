package com.ulticode.common.aspect;

import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 限流切面
 * 基于 Redis 实现 API 限流
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RedisTemplate<String, String> redisTemplate;

    @Around("@annotation(com.ulticode.common.annotation.RateLimit)")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        String key = generateKey(rateLimit, joinPoint);
        String redisKey = "rate-limit:" + key;

        Long count = redisTemplate.opsForValue().increment(redisKey);

        if (count != null && count == 1) {
            redisTemplate.expire(redisKey, rateLimit.period(), TimeUnit.SECONDS);
        }

        if (count != null && count > rateLimit.limit()) {
            log.warn("Rate limit exceeded for key: {}", key);
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                "Rate limit exceeded. Please try again later.");
        }

        return joinPoint.proceed();
    }

    private String generateKey(RateLimit rateLimit, ProceedingJoinPoint joinPoint) {
        String key = rateLimit.key();
        String ip = getClientIp();

        if (key.isEmpty()) {
            // 使用类名 + 方法名 + IP 作为默认 key
            String className = joinPoint.getTarget().getClass().getSimpleName();
            String methodName = joinPoint.getSignature().getName();
            key = className + ":" + methodName + ":" + ip;
        } else {
            // 显式指定的 key 也需要追加 IP 维度，确保按用户/IP 分别限流
            key = key + ":" + ip;
        }

        return key;
    }

    private String getClientIp() {
        ServletRequestAttributes attributes =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }

        HttpServletRequest request = attributes.getRequest();

        // Prefer X-Real-IP (set by nginx, not spoofable by clients)
        String ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.contains(",") ? ip.split(",")[0].trim() : ip;
        }

        // Fallback to remote address (direct connection)
        ip = request.getRemoteAddr();
        return ip != null ? ip : "unknown";
    }
}
