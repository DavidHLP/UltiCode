package com.ulticode.common.response;

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
public record PaginationRequest(int page, int pageSize) {

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
     * Zero-based offset for SQL {@code LIMIT ? OFFSET ?} queries.
     *
     * <p>Overflow-safe: uses {@link Math#multiplyExact} to detect the (extremely
     * unlikely on a capped page-size) case where {@code (page - 1) * pageSize}
     * would overflow {@code int}. Callers receiving an {@link ArithmeticException}
     * should treat the request as out-of-range.
     *
     * @return {@code (page - 1) * pageSize}
     * @throws ArithmeticException if the computation overflows {@code int}
     */
    public int offset() {
        return Math.multiplyExact(page - 1, pageSize);
    }
}
