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
        // R8.2 / F-27: substitute {paramName} placeholders with method
        // arguments so per-resource key templates (e.g.
        // "contest:virtual-start:{id}") resolve to per-resource buckets.
        key = substitutePlaceholders(key, joinPoint);
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

    /**
     * R8.2 / F-27: replace {@code {paramName}} placeholders in the rate
     * limit key template with the corresponding method argument's
     * {@code toString()}. Used to scope rate limit buckets per resource
     * (e.g. {@code "contest:virtual-start:{id}"} resolves to
     * {@code "contest:virtual-start:contest-123:user:u-7"}).
     *
     * <p>Unresolved placeholders are left in place so a typo in the
     * template name does not silently widen the bucket. Wildcard
     * characters in the resolved value are NOT escaped; callers should
     * not put colons inside resource ids.
     */
    private String substitutePlaceholders(String template, ProceedingJoinPoint joinPoint) {
        if (template == null || !template.contains("{")) {
            return template;
        }
        java.lang.reflect.Parameter[] params = ((org.aspectj.lang.reflect.MethodSignature)
                joinPoint.getSignature()).getMethod().getParameters();
        Object[] args = joinPoint.getArgs();
        StringBuilder out = new StringBuilder(template.length());
        int i = 0;
        while (i < template.length()) {
            int open = template.indexOf('{', i);
            if (open < 0) {
                out.append(template, i, template.length());
                break;
            }
            out.append(template, i, open);
            int close = template.indexOf('}', open + 1);
            if (close < 0) {
                // Unterminated placeholder: leave the rest as-is.
                out.append(template, open, template.length());
                break;
            }
            String name = template.substring(open + 1, close);
            String replacement = lookupParam(params, args, name);
            out.append(replacement != null ? replacement : "{" + name + "}");
            i = close + 1;
        }
        return out.toString();
    }

    private String lookupParam(java.lang.reflect.Parameter[] params, Object[] args, String name) {
        if (params == null || args == null) return null;
        for (int i = 0; i < params.length; i++) {
            // @PathVariable / @RequestParam may rename; check both raw and
            // the parameter's name. Default Java parameter name is "argN"
            // without -parameters compilation, so we additionally honour
            // the explicit parameter name in the @PathVariable annotation.
            String pname = params[i].getName();
            if (name.equals(pname) && i < args.length) {
                return String.valueOf(args[i]);
            }
        }
        return null;
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
