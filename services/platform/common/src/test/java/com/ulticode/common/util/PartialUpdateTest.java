package com.ulticode.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PartialUpdate}.
 *
 * <p>Covers the pure-Java entity variants only. The {@code LambdaUpdateWrapper}
 * variants were extracted to
 * {@code com.ulticode.admin.config.MybatisPlusPartialUpdate} (and its tests) so
 * backend-common can stay free of MyBatis-Plus, as enforced by
 * {@code BackendCommonArchTest#COMMON_MUST_NOT_DEPEND_ON_MYBATIS}.
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

    // ===== fixtures =====

    private record Dto(String value) {}

    private record BoxedDto(Integer count) {}
}
