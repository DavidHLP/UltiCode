package com.ulticode.common.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests for {@link PaginationRequest}, focused on the normalized factory,
 * the page-size cap and the explicit overflow-safety contract on
 * {@link PaginationRequest#offset()}.
 */
@DisplayName("PaginationRequest")
class PaginationRequestTest {

    @Nested
    @DisplayName("normalization rules")
    class Normalization {

        @Test
        @DisplayName("null page falls back to DEFAULT_PAGE")
        void nullPageDefaults() {
            PaginationRequest r = PaginationRequest.of(null, 10);
            assertThat(r.page()).isEqualTo(PaginationRequest.DEFAULT_PAGE);
        }

        @Test
        @DisplayName("null pageSize falls back to DEFAULT_PAGE_SIZE")
        void nullPageSizeDefaults() {
            PaginationRequest r = PaginationRequest.of(1, null);
            assertThat(r.pageSize()).isEqualTo(PaginationRequest.DEFAULT_PAGE_SIZE);
        }

        @Test
        @DisplayName("pageSize above MAX_PAGE_SIZE is capped")
        void pageSizeCapped() {
            PaginationRequest r = PaginationRequest.of(1, 999);
            assertThat(r.pageSize()).isEqualTo(PaginationRequest.MAX_PAGE_SIZE);
        }

        @Test
        @DisplayName("negative page falls back to DEFAULT_PAGE")
        void negativePageDefaults() {
            PaginationRequest r = PaginationRequest.of(-5, 10);
            assertThat(r.page()).isEqualTo(PaginationRequest.DEFAULT_PAGE);
        }

        @Test
        @DisplayName("explicit defaultPageSize is honored when pageSize is null")
        void explicitDefaultPageSizeHonored() {
            PaginationRequest r = PaginationRequest.of(null, null, 10);
            assertThat(r.pageSize()).isEqualTo(10);
        }

        @Test
        @DisplayName("defaultPageSize must be &gt;0 and &le;MAX_PAGE_SIZE")
        void invalidDefaultPageSizeRejected() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> PaginationRequest.of(1, 1, 0));
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> PaginationRequest.of(1, 1, -7));
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> PaginationRequest.of(1, 1, 9999));
        }
    }

    @Nested
    @DisplayName("offset() — default widened to long")
    class Offset {

        @Test
        @DisplayName("offset for page 1 is always zero")
        void page1OffsetIsZero() {
            assertThat(PaginationRequest.of(1, 20).offset()).isZero();
        }

        @Test
        @DisplayName("offset for page n is (n-1)*pageSize")
        void generalOffset() {
            assertThat(PaginationRequest.of(3, 25).offset()).isEqualTo(50L);
        }

        @Test
        @DisplayName("offset stays inside long range even at MAX_PAGE_SIZE * MAX_PAGE")
        void offsetStaysInsideLong() {
            PaginationRequest r = new PaginationRequest(Integer.MAX_VALUE, PaginationRequest.MAX_PAGE_SIZE);
            // (~21.47e6 - 1) * 100 = ~2.147e9 — well inside long.
            long off = r.offset();
            assertThat(off).isPositive();
            assertThat(off).isLessThan(Long.MAX_VALUE);
        }
    }

    @Nested
    @DisplayName("isOffsetOverflow() defensive signal")
    class OverflowSignal {

        @Test
        @DisplayName("small pages do not overflow int")
        void smallPagesNotOverflow() {
            assertThat(PaginationRequest.of(1, 20).isOffsetOverflow()).isFalse();
            assertThat(PaginationRequest.of(1000, 100).isOffsetOverflow()).isFalse();
        }

        @Test
        @DisplayName("MAX_PAGE * MAX_PAGE_SIZE overflows int but stays inside long")
        void maxPageOverflowsIntNotLong() {
            PaginationRequest r = new PaginationRequest(Integer.MAX_VALUE, PaginationRequest.MAX_PAGE_SIZE);
            assertThat(r.isOffsetOverflow()).isTrue();
            assertThat(r.offset()).isLessThan(Long.MAX_VALUE);
        }
    }

    @Nested
    @DisplayName("offsetExact() — strict int with ArithmeticException")
    class OffsetExact {

        @Test
        @DisplayName("small offsets round-trip exactly")
        void smallOffsetsRoundTrip() {
            assertThat(PaginationRequest.of(3, 25).offsetExact()).isEqualTo(50);
        }

        @Test
        @DisplayName("MAX_PAGE * MAX_PAGE_SIZE throws ArithmeticException (defensive)")
        void maxPageThrowsArithmeticException() {
            PaginationRequest r = new PaginationRequest(Integer.MAX_VALUE, PaginationRequest.MAX_PAGE_SIZE);
            assertThatExceptionOfType(ArithmeticException.class)
                    .isThrownBy(r::offsetExact);
        }
    }
}