package com.ulticode.modules.subscription.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for premium access control on controller methods or types.
 * Specifies that the user must have an active premium subscription to access the endpoint.
 *
 * Example usage:
 * <pre>
 * {@code
 * @RequirePremium
 * @GetMapping("/premium-content")
 * public ResponseEntity<PremiumContent> getPremiumContent() {
 *     return ResponseEntity.ok(premiumService.getContent());
 * }
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePremium {
}
