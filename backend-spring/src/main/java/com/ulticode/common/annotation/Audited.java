package com.ulticode.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method for audit logging.
 * The AuditAspect intercepts methods annotated with @Audited and records
 * the action, entity type, and optionally old/new state.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /**
     * Audit action constant, e.g. {@code AuditActionUtil.CREATE_USER}.
     */
    String action();

    /**
     * Entity type constant, e.g. {@code AuditActionUtil.ENTITY_USER}.
     */
    String entityType();

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
