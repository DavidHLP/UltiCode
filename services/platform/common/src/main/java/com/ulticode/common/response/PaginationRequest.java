package com.ulticode.common.response;

import java.io.Serializable;

/**
 * Normalized pagination request — the single source of truth for the rules
 * "default page 1, default page-size 20, hard cap 100".
 *
 * <p>Prior to this deep module, the same normalization logic was hand-rolled
 * across 26 service implementations with silent drift: default page-size
 * varied from 10 to 50, the cap from 50 to 100, and variable names from
 * {@code currentPage} to {@code currentLimit} to {@code safeLimit}. See
 * {@code /tmp/architecture-review-1783420414.html} candidate 7.
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * // before — 4 lines of drift-prone normalization per service
 * int currentPage = (page != null && page > 0) ? page : 1;
 * int currentPageSize = (pageSize != null && pageSize > 0) ? pageSize : 20;
 * currentPageSize = Math.min(currentPageSize, 100);
 * return PageResult.of(items, total, currentPage, currentPageSize);
 *
 * // after — one seam, one rule
 * PaginationRequest req = PaginationRequest.of(page, pageSize);
 * return PageResult.of(items, total, req);
 * }</pre>
 *
 * <p>Callers whose domain legitimately needs a different default page-size
 * (e.g. admin tables default to 10, contest ranking defaults to 50) use the
 * {@link #of(Integer, Integer, int)} overload. The hard cap of 100 is
 * non-negotiable — it protects against page-size DoS.
 *
 * <p>This is a deep module per ADR-0011: a small interface (two factory
 * methods + two accessors + offset) hiding the entire normalization rule set.
 * Tests cross the same seam as callers.
 */
public record PaginationRequest(int page, int pageSize) implements Serializable {

    /** Default page number (1-based). */
    public static final int DEFAULT_PAGE = 1;

    /** Default page size when caller supplies null/zero/negative. */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** Hard maximum page size — protects against page-size DoS. */
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * Normalize a raw page/page-size pair using platform defaults
     * (page 1, size 20, cap 100).
     *
     * @param page     raw page parameter from the request; null/zero/negative → 1
     * @param pageSize raw page-size parameter; null/zero/negative → 20, &gt;100 → 100
     * @return a normalized {@link PaginationRequest}
     */
    public static PaginationRequest of(Integer page, Integer pageSize) {
        return of(page, pageSize, DEFAULT_PAGE_SIZE);
    }

    /**
     * Normalize a raw page/page-size pair with a caller-specified default
     * page-size. Use this only when the domain legitimately needs a different
     * default (e.g. admin tables default to 10).
     *
     * <p>The hard cap of {@link #MAX_PAGE_SIZE} is still enforced regardless
     * of {@code defaultPageSize}.
     *
     * @param page            raw page parameter; null/zero/negative → 1
     * @param pageSize        raw page-size parameter; null/zero/negative → {@code defaultPageSize}, &gt;100 → 100
     * @param defaultPageSize the default to use when {@code pageSize} is unset (must be &gt; 0 and ≤ {@link #MAX_PAGE_SIZE})
     * @return a normalized {@link PaginationRequest}
     */
    public static PaginationRequest of(Integer page, Integer pageSize, int defaultPageSize) {
        if (defaultPageSize <= 0 || defaultPageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "defaultPageSize must be in (0, " + MAX_PAGE_SIZE + "], got " + defaultPageSize);
        }
        int normalizedPage = (page != null && page > 0) ? page : DEFAULT_PAGE;
        int normalizedPageSize = (pageSize != null && pageSize > 0)
                ? Math.min(pageSize, MAX_PAGE_SIZE)
                : defaultPageSize;
        return new PaginationRequest(normalizedPage, normalizedPageSize);
    }

    /**
     * Zero-based SQL offset as a {@code long}.
     *
     * <p>Returns {@code (page - 1) * pageSize} widened to a {@code long} so
     * that callers using MyBatis {@code LIMIT ? OFFSET ?} or JDBC
     * {@code PreparedStatement.setLong(...)} do not have to insert their own
     * widening casts. The 100-page-size cap makes {@code int} overflow
     * practically impossible for {@code page * pageSize}, but we still
     * compute the widened form here so the boundary check lives in one
     * place.
     *
     * <p>Overflow behaviour: with {@link #MAX_PAGE_SIZE} = 100, a {@code page}
     * of {@link Integer#MAX_VALUE} yields an offset of about
     * {@code 2.1e11}, comfortably inside the {@code long} range and still
     * far below {@link Long#MAX_VALUE}. {@link #isOffsetOverflow()} returns
     * {@code true} when the offset would not fit in a 32-bit {@code int}
     * (defensive: callers that have to write the offset to a narrow
     * parameter type can branch on this).
     *
     * @return the zero-based offset, widened to {@code long}
     */
    public long offset() {
        return (long) (page - 1) * (long) pageSize;
    }

    /**
     * Strict zero-based offset for callers that must surface the offset on
     * the wire as a 32-bit signed integer (rare; SQL drivers accept long).
     *
     * <p>{@link ArithmeticException} is reserved for genuine integer
     * overflow that a {@code long} cannot help with. With the
     * {@link #MAX_PAGE_SIZE} cap the only way to overflow {@code long} is
     * if {@code page} exceeds roughly 92 quadrillion &mdash; which Java
     * cannot represent as an {@code int} anyway.
     *
     * @return the zero-based offset as an {@code int}
     * @throws ArithmeticException if the widened offset does not fit in
     *         an {@code int} (defensive: never expected with the
     *         page-size cap in effect)
     */
    public int offsetExact() {
        return Math.multiplyExact(page - 1, pageSize);
    }

    /**
     * @return {@code true} when the widened offset no longer fits in a
     *         32-bit signed {@code int}; a defensive signal so callers
     *         using narrow types can branch without computing the
     *         offset themselves.
     */
    public boolean isOffsetOverflow() {
        long widened = offset();
        return widened < Integer.MIN_VALUE || widened > Integer.MAX_VALUE;
    }
}
