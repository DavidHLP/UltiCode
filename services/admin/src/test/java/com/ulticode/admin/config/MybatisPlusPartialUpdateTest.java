package com.ulticode.admin.config;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MybatisPlusPartialUpdate}.
 *
 * <p>Migrated from {@code backend-common}'s {@code PartialUpdateTest} when the
 * {@code LambdaUpdateWrapper} variants were extracted out of backend-common to
 * satisfy {@code BackendCommonArchTest#COMMON_MUST_NOT_DEPEND_ON_MYBATIS}.
 *
 * <p>The assertions cover the null/blank/whitespace short-circuit paths only:
 * the value is extracted before any wrapper call, so on the skip path the
 * {@code SFunction} is never invoked and the wrapper's {@code sqlSet} stays
 * {@code null}. A positive (set) path is not exercised here because it would
 * require the MyBatis-Plus lambda cache to resolve the {@code SFunction} to a
 * column name, which needs a full mapper context.
 */
@DisplayName("MybatisPlusPartialUpdate")
class MybatisPlusPartialUpdateTest {

    // ===== setIfPresentWrapper (LambdaUpdateWrapper) =====

    @Nested
    @DisplayName("setIfPresentWrapper (LambdaUpdateWrapper)")
    class SetIfPresentWrapper {

        @Test
        @DisplayName("null value → wrapper is not mutated")
        void nullValueSkipsWrapper() {
            LambdaUpdateWrapper<WrapperEntity> wrapper = new LambdaUpdateWrapper<>();
            // The MyBatis-Plus SFunction is not invoked because the value is
            // null — the getter short-circuits before the wrapper is touched.
            MybatisPlusPartialUpdate.setIfPresentWrapper(wrapper, new Dto(null), Dto::value, WrapperEntity::getName);
            assertThat(wrapper.getSqlSet()).isNull();
        }

        @Test
        @DisplayName("value extraction happens before any wrapper call (no MyBatis-Plus lambda cache needed for null path)")
        void nullPathDoesNotResolveColumn() {
            // The wrapper API requires the MyBatis-Plus lambda cache to
            // resolve SFunction → column. The null path never calls
            // wrapper.set(), so the cache is never touched. This test
            // exercises that contract on a freshly-constructed wrapper.
            LambdaUpdateWrapper<WrapperEntity> wrapper = new LambdaUpdateWrapper<>();
            MybatisPlusPartialUpdate.setIfPresentWrapper(wrapper, new Dto(null), Dto::value, WrapperEntity::getName);
            MybatisPlusPartialUpdate.setIfPresentWrapper(wrapper, new Dto(null), Dto::value, WrapperEntity::getEmail);
            assertThat(wrapper.getSqlSet()).isNull();
        }
    }

    // ===== setIfPresentTextWrapper (text-aware wrapper) =====

    @Nested
    @DisplayName("setIfPresentTextWrapper (text-aware wrapper)")
    class SetIfPresentTextWrapper {

        @Test
        @DisplayName("empty string → wrapper is not mutated")
        void emptyStringSkipsWrapper() {
            LambdaUpdateWrapper<WrapperEntity> wrapper = new LambdaUpdateWrapper<>();
            MybatisPlusPartialUpdate.setIfPresentTextWrapper(wrapper, new Dto(""), Dto::value, WrapperEntity::getName);
            assertThat(wrapper.getSqlSet()).isNull();
        }

        @Test
        @DisplayName("whitespace-only string → wrapper is not mutated")
        void whitespaceOnlySkipsWrapper() {
            LambdaUpdateWrapper<WrapperEntity> wrapper = new LambdaUpdateWrapper<>();
            MybatisPlusPartialUpdate.setIfPresentTextWrapper(wrapper, new Dto("   "), Dto::value, WrapperEntity::getName);
            assertThat(wrapper.getSqlSet()).isNull();
        }

        @Test
        @DisplayName("null value → wrapper is not mutated")
        void nullValueSkipsWrapper() {
            LambdaUpdateWrapper<WrapperEntity> wrapper = new LambdaUpdateWrapper<>();
            MybatisPlusPartialUpdate.setIfPresentTextWrapper(wrapper, new Dto(null), Dto::value, WrapperEntity::getName);
            assertThat(wrapper.getSqlSet()).isNull();
        }
    }

    // ===== fixtures =====

    private record Dto(String value) {}

    /**
     * Minimal bean used as the {@link LambdaUpdateWrapper} generic type.
     * Only getter methods are needed because the null/blank short-circuit
     * paths never invoke the SFunction (no MyBatis-Plus column resolution).
     */
    @SuppressWarnings("unused")
    public static class WrapperEntity {
        private String name;
        private String email;

        public String getName() { return name; }
        public String getEmail() { return email; }
    }
}
