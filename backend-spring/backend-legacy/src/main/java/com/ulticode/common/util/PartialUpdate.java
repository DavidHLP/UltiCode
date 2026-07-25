package com.ulticode.common.util;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.springframework.util.StringUtils;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Copy non-null DTO fields onto an entity (or into a MyBatis-Plus
 * {@code LambdaUpdateWrapper}) without the {@code if (dto.getX() != null)
 * entity.setX(dto.getX())} boilerplate that pollutes admin/service impls.
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
 * <h2>Examples</h2>
 * <pre>{@code
 * // In-memory entity update
 * PartialUpdate.setIfPresent(dto, UpdateUserDTO::getName, user::setName);
 * PartialUpdate.setIfPresent(dto, dto -> dto.getIsActive(), user::setIsActive);
 *
 * // String-typed field (use hasText so an empty string is treated as "absent")
 * PartialUpdate.setIfPresentText(dto, AdminUpdateUserDTO::getUsername, user::setUsername);
 *
 * // LambdaUpdateWrapper (SQL-level update, no entity in memory)
 * PartialUpdate.setIfPresentWrapper(wrapper, dto, AdminUpdateUserDTO::getUsername, User::getUsername);
 * PartialUpdate.setIfPresentTextWrapper(wrapper, dto, AdminUpdateUserDTO::getUsername, User::getUsername);
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
     * {@link StringUtils#hasText(CharSequence)}), call {@code setter.accept(value)}.
     * Use this for {@code String} DTO fields where an empty string should be
     * treated as "the user did not set this field".
     */
    public static <D> void setIfPresentText(D dto, Function<D, String> getter, Consumer<String> setter) {
        String value = getter.apply(dto);
        if (StringUtils.hasText(value)) {
            setter.accept(value);
        }
    }

    /**
     * {@code LambdaUpdateWrapper} variant of {@link #setIfPresent} for
     * SQL-level partial updates. The wrapper accumulates the set clauses
     * until the caller invokes {@code mapper.update(null, wrapper)}.
     *
     * @param wrapper the MyBatis-Plus update wrapper to mutate
     * @param dto     the DTO carrying the partial-update intent
     * @param getter  extracts the field value from the DTO
     * @param column  the MyBatis-Plus column reference (e.g. {@code User::getUsername})
     * @param <E>     entity type
     * @param <D>     DTO type
     */
    public static <E, D, V> void setIfPresentWrapper(
            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<E> wrapper,
            D dto, Function<D, V> getter, SFunction<E, V> column) {
        V value = getter.apply(dto);
        if (value != null) {
            wrapper.set(column, value);
        }
    }

    /**
     * {@code LambdaUpdateWrapper} variant of {@link #setIfPresentText} for
     * {@code String} columns. Skips empty / blank values.
     */
    public static <E, D> void setIfPresentTextWrapper(
            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<E> wrapper,
            D dto, Function<D, String> getter, SFunction<E, String> column) {
        String value = getter.apply(dto);
        if (StringUtils.hasText(value)) {
            wrapper.set(column, value);
        }
    }
}
