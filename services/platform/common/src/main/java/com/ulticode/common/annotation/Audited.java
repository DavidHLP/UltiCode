package com.ulticode.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method for audit logging.
 * The AuditAspect intercepts methods annotated with @Audited and records
 * the action, entity type, performer, IP, user agent, and optionally old/new state.
 *
 * <p>For detailed old/new value capture, use {@link com.ulticode.common.util.AuditContext}
 * inside the method body before/after the mutation.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /**
     * Audit action constant, e.g. {@code AuditVocabulary.CREATE_USER}.
     */
    String action();

    /**
     * Entity type constant, e.g. {@code AuditVocabulary.ENTITY_USER}.
     */
    String entityType();

    /**
     * Method parameter name to extract as the target user ID (for logForUser pattern).
     * Empty string means no automatic userId extraction — use {@link AuditContext#setUserId} instead.
     */
    String userIdFrom() default "";

    /**
     * Method parameter name to extract as the entity ID.
     * Empty string means fall back to result.getId() via reflection or {@link AuditContext#setEntityId}.
     */
    String entityIdFrom() default "";

    /**
     * Whether to attempt capturing the old entity state before the method runs.
     * Disabled automatically when the entity ID cannot be resolved.
     */
    boolean captureOldState() default true;

    /**
     * Whether to capture the new state from the method return value.
     */
    boolean captureNewState() default true;
}
