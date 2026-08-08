package com.ulticode.websecurity.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rate-limit annotation — marks controller methods for rate-limit enforcement.
 *
 * <p>Applied to controller methods; the {@link com.ulticode.websecurity.aspect.RateLimitAspect}
 * intercepts each call, resolves the bucket key (user-id when authenticated,
 * client-IP otherwise), and delegates the atomic check to the configured
 * {@link com.ulticode.websecurity.ratelimiter.RateLimiter} adapter.
 *
 * <p>Key template supports {@code {paramName}} placeholder substitution via
 * {@code @PathVariable} / {@code @RequestParam} annotation names, allowing
 * per-resource buckets (e.g. {@code "contest:virtual-start:{id}"}).
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    /**
     * Rate-limit key prefix. Supports {@code {paramName}} placeholders
     * resolved from {@code @PathVariable} / {@code @RequestParam} method
     * arguments.
     */
    String key() default "";

    /**
     * Maximum acquisitions allowed within the time window.
     */
    int limit() default 100;

    /**
     * Time window length in seconds.
     */
    int period() default 60;
}
