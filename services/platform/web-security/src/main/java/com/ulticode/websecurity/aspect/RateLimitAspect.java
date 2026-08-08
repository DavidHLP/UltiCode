package com.ulticode.websecurity.aspect;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.websecurity.annotation.RateLimit;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.websecurity.ratelimiter.AcquisitionVerdict;
import com.ulticode.websecurity.ratelimiter.RateLimiter;
import com.ulticode.websecurity.util.ClientIpResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;

/**
 * AOP advice for {@link RateLimit} — owns request-context key generation
 * (placeholder substitution + user/IP detection). The actual rate check —
 * Redis Lua script, key prefix, counter logic — is delegated to the
 * {@link RateLimiter} port.
 *
 * <p>Two adapters justify the seam: {@link com.ulticode.websecurity.ratelimiter.RedisRateLimiter}
 * in prod, {@link com.ulticode.websecurity.ratelimiter.InMemoryRateLimiter} in
 * tests. The aspect is unit-testable without Redis.
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final CurrentUserProvider currentUserProvider;

    @Around("@annotation(com.ulticode.websecurity.annotation.RateLimit)")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        String key = generateKey(rateLimit, joinPoint);

        AcquisitionVerdict verdict = rateLimiter.tryAcquire(key, rateLimit.limit(), rateLimit.period());
        if (!verdict.allowed()) {
            log.warn("Rate limit exceeded for key: {}, retryAfter: {}s", key, verdict.retryAfterSeconds());
            throw new BusinessException(BaseErrorCode.TOO_MANY_REQUESTS,
                    "Rate limit exceeded. Please try again in " + verdict.retryAfterSeconds() + " seconds.");
        }

        return joinPoint.proceed();
    }

    private String generateKey(RateLimit rateLimit, ProceedingJoinPoint joinPoint) {
        String key = rateLimit.key();
        // R8.2 / F-27: substitute {paramName} placeholders with method
        // arguments so per-resource key templates (e.g.
        // "contest:virtual-start:{id}") resolve to per-resource buckets.
        key = substitutePlaceholders(key, joinPoint);
        String userId = currentUserProvider.getCurrentUserId();

        if (userId != null) {
            key = key + ":user:" + userId;
        } else {
            String ip = clientIpResolver.resolveCurrent();
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
        java.lang.reflect.Parameter[] params = ((MethodSignature)
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
        // Look up by @PathVariable / @RequestParam annotation name (and value for older Spring idioms).
        for (int i = 0; i < params.length; i++) {
            if (i >= args.length) break;
            var pv = params[i].getAnnotation(org.springframework.web.bind.annotation.PathVariable.class);
            if (pv != null && name.equals(pv.name().isEmpty() ? pv.value() : pv.name())) {
                return String.valueOf(args[i]);
            }
            var rp = params[i].getAnnotation(org.springframework.web.bind.annotation.RequestParam.class);
            if (rp != null && name.equals(rp.name().isEmpty() ? rp.value() : rp.name())) {
                return String.valueOf(args[i]);
            }
        }
        return null;
    }
}
