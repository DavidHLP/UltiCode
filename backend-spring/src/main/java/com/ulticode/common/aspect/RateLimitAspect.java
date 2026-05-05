package com.ulticode.common.aspect;

import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.List;
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

    private static final String RATE_LIMIT_SCRIPT =
            "local count = redis.call('INCR', KEYS[1]) " +
            "redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
            "return count";

    private final StringRedisTemplate redisTemplate;

    @Around("@annotation(com.ulticode.common.annotation.RateLimit)")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        String key = generateKey(rateLimit, joinPoint);
        String redisKey = "rate-limit:" + key;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(RATE_LIMIT_SCRIPT, Long.class);
        Long count = redisTemplate.execute(script, List.of(redisKey), String.valueOf(rateLimit.period()));

        if (count != null && count > rateLimit.limit()) {
            Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
            log.warn("Rate limit exceeded for key: {}, ttl: {}s", key, ttl);
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    "Rate limit exceeded. Please try again in " + (ttl != null ? ttl : rateLimit.period()) + " seconds.");
        }

        return joinPoint.proceed();
    }

    private String generateKey(RateLimit rateLimit, ProceedingJoinPoint joinPoint) {
        String key = rateLimit.key();
        String userId = SecurityUtil.getCurrentUserId();

        if (userId != null) {
            key = key + ":user:" + userId;
        } else {
            String ip = getClientIp();
            if (key.isEmpty()) {
                String className = joinPoint.getTarget().getClass().getSimpleName();
                String methodName = joinPoint.getSignature().getName();
                key = className + ":" + methodName;
            }
            key = key + ":ip:" + ip;
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
