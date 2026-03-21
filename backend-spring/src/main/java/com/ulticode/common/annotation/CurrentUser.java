package com.ulticode.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to inject the current authenticated user into a controller method parameter.
 * This annotation should be used with a parameter resolver to automatically resolve
 * the current user from the security context.
 *
 * Example usage:
 * <pre>
 * {@code
 * @GetMapping("/profile")
 * public ResponseEntity<UserProfile> getProfile(@CurrentUser User user) {
 *     return ResponseEntity.ok(userService.getProfile(user.getId()));
 * }
 * }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
