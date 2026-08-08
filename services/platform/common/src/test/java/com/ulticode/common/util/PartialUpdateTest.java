package com.ulticode.common.util;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PartialUpdate}.
 *
 * <p>Migrated from backend-legacy to backend-common so the tests live next to
 * the class they verify. The wrapper tests previously used
 * {@code com.ulticode.modules.user.entity.User} as the LambdaUpdateWrapper
 * generic; this version uses a common-local fixture ({@link WrapperEntity})
 * to avoid a cross-module dependency while exercising the same null/blank
 * short-circuit paths.
 */
@DisplayName("PartialUpdate")
class PartialUpdateTest {

    // ===== setIfPresent (entity in-memory) =====

    @Nested
    @DisplayName("setIfPresent (entity)")
    class SetIfPresent {

        @Test
        @DisplayName("null value → setter is not invoked")
        void nullValueSkipsSetter() {
            AtomicReference<String> captured = new AtomicReference<>("UNTOUCHED");
            PartialUpdate.setIfPresent(new Dto(null), Dto::value, captured::set);
            assertThat(captured.get()).isEqualTo("UNTOUCHED");
        }

        @Test
        @DisplayName("valid value → setter receives the value")
        void validValueInvokesSetter() {
            AtomicReference<String> captured = new AtomicReference<>();
            PartialUpdate.setIfPresent(new Dto("hello"), Dto::value, captured::set);
            assertThat(captured.get()).isEqualTo("hello");
        }

        @Test
        @DisplayName("empty string is treated as a value (use setIfPresentText for text-aware skip)")
        void emptyStringTreatedAsValue() {
            AtomicReference<String> captured = new AtomicReference<>();
            PartialUpdate.setIfPresent(new Dto(""), Dto::value, captured::set);
            // setIfPresent does not inspect content, only null-ness.
            assertThat(captured.get()).isEqualTo("");
        }

        @Test
        @DisplayName("works for non-String field types")
        void worksForNonString() {
            AtomicReference<Integer> captured = new AtomicReference<>();
            PartialUpdate.setIfPresent(new BoxedDto(42), BoxedDto::count, captured::set);
            assertThat(captured.get()).isEqualTo(42);
        }
    }

    // ===== setIfPresentText (text-aware skip) =====

    @Nested
    @DisplayName("setIfPresentText (text-aware skip)")
    class SetIfPresentText {

        @Test
        @DisplayName("null value → setter is not invoked")
        void nullValueSkipsSetter() {
            AtomicReference<String> captured = new AtomicReference<>("UNTOUCHED");
            PartialUpdate.setIfPresentText(new Dto(null), Dto::value, captured::set);
            assertThat(captured.get()).isEqualTo("UNTOUCHED");
        }

        @Test
        @DisplayName("empty string → setter is not invoked")
        void emptyStringSkipsSetter() {
            AtomicReference<String> captured = new AtomicReference<>("UNTOUCHED");
            PartialUpdate.setIfPresentText(new Dto(""), Dto::value, captured::set);
            assertThat(captured.get()).isEqualTo("UNTOUCHED");
        }

        @Test
        @DisplayName("whitespace-only string → setter is not invoked")
        void whitespaceOnlySkipsSetter() {
            AtomicReference<String> captured = new AtomicReference<>("UNTOUCHED");
            PartialUpdate.setIfPresentText(new Dto("   "), Dto::value, captured::set);
            assertThat(captured.get()).isEqualTo("UNTOUCHED");
        }

        @Test
        @DisplayName("non-blank value → setter receives the value")
        void nonBlankValueInvokesSetter() {
            AtomicReference<String> captured = new AtomicReference<>();
            PartialUpdate.setIfPresentText(new Dto("hello"), Dto::value, captured::set);
            assertThat(captured.get()).isEqualTo("hello");
        }
    }

    // ===== setIfPresentWrapper (MyBatis-Plus wrapper) =====

    @Nested
    @DisplayName("setIfPresentWrapper (LambdaUpdateWrapper)")
    class SetIfPresentWrapper {

        @Test
        @DisplayName("null value → wrapper is not mutated")
        void nullValueSkipsWrapper() {
            LambdaUpdateWrapper<WrapperEntity> wrapper = new LambdaUpdateWrapper<>();
            // The MyBatis-Plus SFunction is not invoked because the value is
            // null — the getter short-circuits before the wrapper is touched.
            PartialUpdate.setIfPresentWrapper(wrapper, new Dto(null), Dto::value, WrapperEntity::getName);
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
            PartialUpdate.setIfPresentWrapper(wrapper, new Dto(null), Dto::value, WrapperEntity::getName);
            PartialUpdate.setIfPresentWrapper(wrapper, new Dto(null), Dto::value, WrapperEntity::getEmail);
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
            PartialUpdate.setIfPresentTextWrapper(wrapper, new Dto(""), Dto::value, WrapperEntity::getName);
            assertThat(wrapper.getSqlSet()).isNull();
        }

        @Test
        @DisplayName("whitespace-only string → wrapper is not mutated")
        void whitespaceOnlySkipsWrapper() {
            LambdaUpdateWrapper<WrapperEntity> wrapper = new LambdaUpdateWrapper<>();
            PartialUpdate.setIfPresentTextWrapper(wrapper, new Dto("   "), Dto::value, WrapperEntity::getName);
            assertThat(wrapper.getSqlSet()).isNull();
        }

        @Test
        @DisplayName("null value → wrapper is not mutated")
        void nullValueSkipsWrapper() {
            LambdaUpdateWrapper<WrapperEntity> wrapper = new LambdaUpdateWrapper<>();
            PartialUpdate.setIfPresentTextWrapper(wrapper, new Dto(null), Dto::value, WrapperEntity::getName);
            assertThat(wrapper.getSqlSet()).isNull();
        }
    }

    // ===== fixtures =====

    private record Dto(String value) {}

    private record BoxedDto(Integer count) {}

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
