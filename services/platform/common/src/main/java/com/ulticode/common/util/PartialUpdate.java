package com.ulticode.common.util;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Copy non-null DTO fields onto an entity without the
 * {@code if (dto.getX() != null) entity.setX(dto.getX())} boilerplate that
 * pollutes admin/service impls.
 *
 * <p>Two patterns were duplicated 30+ times across
 * {@code AdminProblemListServiceImpl}, {@code AdminContestServiceImpl},
 * {@code UserManagementServiceImpl}, {@code AdminNotificationServiceImpl},
 * {@code AdminProblemServiceImpl}, {@code ProblemListServiceImpl}, and
 * {@code NotificationServiceImpl}. After this seam extraction each call
 * site is one line per field and the per-field null check is centralized
 * here.
 *
 * <p>This module owns the partial-copy shape, not the audit context
 * (callers wrap the call inside their own {@code AuditContext} block —
 * the audit policy is service-level, the field-copy policy is generic).
 *
 * <p>This class is intentionally free of MyBatis-Plus references so
 * backend-common stays reusable by every Dubbo contract module (enforced by
 * {@code BackendCommonArchTest#COMMON_MUST_NOT_DEPEND_ON_MYBATIS}). The
 * {@code LambdaUpdateWrapper} variants live in
 * {@code com.ulticode.admin.config.MybatisPlusPartialUpdate}.
 *
 * <h2>Examples</h2>
 * <pre>{@code
 * // In-memory entity update
 * PartialUpdate.setIfPresent(dto, UpdateUserDTO::getName, user::setName);
 * PartialUpdate.setIfPresent(dto, dto -> dto.getIsActive(), user::setIsActive);
 *
 * // String-typed field (use isBlank so an empty string is treated as "absent")
 * PartialUpdate.setIfPresentText(dto, AdminUpdateUserDTO::getUsername, user::setUsername);
 * }</pre>
 */
public final class PartialUpdate {

    private PartialUpdate() {
        // Utility class
    }

    /**
     * If {@code getter.apply(dto)} is non-null, call {@code setter.accept(value)}.
     * String-typed values are NOT considered "absent" if blank — use
     * {@link #setIfPresentText} for text fields where {@code ""} should be
     * treated as "no change".
     */
    public static <D, V> void setIfPresent(D dto, Function<D, V> getter, Consumer<V> setter) {
        V value = getter.apply(dto);
        if (value != null) {
            setter.accept(value);
        }
    }

    /**
     * If {@code getter.apply(dto)} is non-null and non-blank (per
     * {@link String#isBlank()}), call {@code setter.accept(value)}.
     * Use this for {@code String} DTO fields where an empty string should be
     * treated as "the user did not set this field".
     */
    public static <D> void setIfPresentText(D dto, Function<D, String> getter, Consumer<String> setter) {
        String value = getter.apply(dto);
        if (value != null && !value.isBlank()) {
            setter.accept(value);
        }
    }
}
