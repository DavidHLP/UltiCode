package com.ulticode.modules.forum.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Composite validation constraint for the {@code pageSize} parameter of paginated
 * forum list endpoints. The value must be in the inclusive range {@code [1, 50]}.
 *
 * <p>Composes the standard {@link Min} and {@link Max} constraints under
 * {@link ReportAsSingleViolation} so a single validation error is surfaced
 * with the message defined here.
 *
 * <p>Usage:
 * <pre>{@code
 * public Result<PageResult<...>> list(
 *         @RequestParam(required = false, defaultValue = "1") @ForumPage Integer page,
 *         @RequestParam(required = false, defaultValue = "20") @ForumPageSize Integer pageSize) {
 *     ...
 * }
 * }</pre>
 *
 * @see ForumPage
 * @see <a href="https://jakarta.ee/specifications/bean-validation/3.0/jakarta-bean-validation-spec-3.0.html#constraintcomposition">Jakarta Bean Validation §3.3 (Constraint Composition)</a>
 */
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Min(value = 1, message = "pageSize must be at least 1")
@Max(value = 50, message = "pageSize cannot exceed 50")
@Constraint(validatedBy = {})
@ReportAsSingleViolation
public @interface ForumPageSize {

    String message() default "pageSize must be between 1 and 50";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
