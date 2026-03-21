package com.ulticode.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for role-based access control on controller methods or types.
 * Specifies the required roles that a user must have to access the annotated endpoint.
 *
 * Example usage:
 * <pre>
 * {@code
 * @RequireRole("ADMIN")
 * @DeleteMapping("/users/{id}")
 * public ResponseEntity<Void> deleteUser(@PathVariable String id) {
 *     userService.delete(id);
 *     return ResponseEntity.noContent().build();
 * }
 *
 * @RequireRole({"ADMIN", "MODERATOR"})
 * @PostMapping("/moderate")
 * public ResponseEntity<Void> moderateContent(@RequestBody ModerationRequest request) {
 *     moderationService.process(request);
 *     return ResponseEntity.ok().build();
 * }
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /**
     * The roles required to access the annotated method or type.
     * User must have at least one of the specified roles.
     *
     * @return array of role names (without ROLE_ prefix)
     */
    String[] value() default {};
}
