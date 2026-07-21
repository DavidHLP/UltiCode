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
 * Composite validation constraint for the {@code page} parameter of paginated
 * forum list endpoints. The value must be in the inclusive range {@code [1, 1000]}.
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
 * @see ForumPageSize
 * @see <a href="https://jakarta.ee/specifications/bean-validation/3.0/jakarta-bean-validation-spec-3.0.html#constraintcomposition">Jakarta Bean Validation §3.3 (Constraint Composition)</a>
 */
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Min(value = 1, message = "page must be at least 1")
@Max(value = 1000, message = "page cannot exceed 1000")
@Constraint(validatedBy = {})
@ReportAsSingleViolation
public @interface ForumPage {

    String message() default "page must be between 1 and 1000";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
