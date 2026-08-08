package com.ulticode.admin.config;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import java.util.function.Function;
import org.springframework.util.StringUtils;

/**
 * MyBatis-Plus {@link LambdaUpdateWrapper} variants of the partial-update helpers
 * kept in {@code com.ulticode.common.util.PartialUpdate}.
 *
 * <p>These methods live in the admin module (not {@code backend-common}) because
 * they reference MyBatis-Plus types, and
 * {@code com.ulticode.common.architecture.BackendCommonArchTest#COMMON_MUST_NOT_DEPEND_ON_MYBATIS}
 * forbids any {@code com.ulticode.common..} class from depending on
 * {@code com.baomidou..} so backend-common stays reusable by every Dubbo contract
 * module. The pure-Java entity variants remain in backend-common; only the
 * wrapper-coupled variants are hosted here, next to {@link AdminMybatisPlusConfig}.
 *
 * <p>The wrapper accumulates {@code SET} clauses until the caller invokes
 * {@code mapper.update(null, wrapper)}.
 */
public final class MybatisPlusPartialUpdate {

    private MybatisPlusPartialUpdate() {
        // Utility class
    }

    /**
     * If {@code getter.apply(dto)} is non-null, append a {@code SET} clause for
     * {@code column} onto {@code wrapper}.
     *
     * @param wrapper the MyBatis-Plus update wrapper to mutate
     * @param dto     the DTO carrying the partial-update intent
     * @param getter  extracts the field value from the DTO
     * @param column  the MyBatis-Plus column reference (e.g. {@code User::getUsername})
     * @param <E>     entity type
     * @param <D>     DTO type
     * @param <V>     field value type
     */
    public static <E, D, V> void setIfPresentWrapper(
            LambdaUpdateWrapper<E> wrapper,
            D dto, Function<D, V> getter, SFunction<E, V> column) {
        V value = getter.apply(dto);
        if (value != null) {
            wrapper.set(column, value);
        }
    }

    /**
     * If {@code getter.apply(dto)} is non-null and non-blank (per
     * {@link StringUtils#hasText(CharSequence)}), append a {@code SET} clause for
     * the {@code String} {@code column} onto {@code wrapper}. Skips empty / blank
     * values, treating them as "the user did not set this field".
     */
    public static <E, D> void setIfPresentTextWrapper(
            LambdaUpdateWrapper<E> wrapper,
            D dto, Function<D, String> getter, SFunction<E, String> column) {
        String value = getter.apply(dto);
        if (StringUtils.hasText(value)) {
            wrapper.set(column, value);
        }
    }
}
