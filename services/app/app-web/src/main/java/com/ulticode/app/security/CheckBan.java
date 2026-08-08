package com.ulticode.app.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Method-level annotation triggering ban enforcement before execution.
 *
 * <p>App-side replacement for legacy
 * {@code com.ulticode.common.annotation.CheckBan}. Processed by
 * {@link BanCheckAspect}.
 *
 * <p>P7-RELOCATE-SOLUTION-001: required when backend-app stopped depending
 * on backend-legacy.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckBan {
}
