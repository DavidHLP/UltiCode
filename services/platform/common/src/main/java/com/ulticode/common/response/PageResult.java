package com.ulticode.common.response;

import lombok.Data;
import java.io.Serializable;

import java.util.List;

/**
 * Paginated response wrapper for list data.
 * Matches NestJS pagination format exactly for frontend compatibility.
 *
 * @param <T> the type of items in the page
 */
@Data
public class PageResult<T> implements Serializable {
    private static final long serialVersionUID = 1L;


    /**
     * List of items in the current page
     */
    private List<T> items;

    /**
     * Total number of items across all pages
     */
    private Long total;

    /**
     * Current page number (1-based)
     */
    private Integer page;

    /**
     * Number of items per page
     */
    private Integer pageSize;

    /**
     * Total number of pages
     */
    private Integer totalPages;

    /**
     * Explicit degradation marker for reads aggregating multiple owners or
     * caches. {@code null} on local-only / legacy reads and must be treated
     * as {@link DegradationStatus#OK}; never null-implies-degraded.
     */
    private DegradationStatus degradationStatus;

    private PageResult() {
    }

    private PageResult(List<T> items, Long total, Integer page, Integer pageSize, Integer totalPages) {
        this.items = items;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
        this.totalPages = totalPages;
    }

    /**
     * Create a PageResult from pagination parameters
     *
     * @param items    the list of items
     * @param total    total number of items
     * @param page     current page number (1-based)
     * @param pageSize number of items per page
     * @param <T>      the type of items
     * @return PageResult instance
     */
    public static <T> PageResult<T> of(List<T> items, Long total, Integer page, Integer pageSize) {
        int totalPages = 0;
        if (pageSize != null && pageSize > 0) {
            totalPages = (int) Math.ceil((double) total / pageSize);
        }
        return new PageResult<>(items, total, page, pageSize, totalPages);
    }

    /**
     * Create a PageResult from a normalized {@link PaginationRequest}.
     *
     * <p>Preferred over {@link #of(List, Long, Integer, Integer)} — the
     * normalization rules (default page 1, default size 20, cap 100) live
     * behind one seam in {@link PaginationRequest}, eliminating per-service
     * drift. Existing callers may migrate mechanically:
     * <pre>{@code
     * // before
     * int currentPage = (page != null && page > 0) ? page : 1;
     * int currentPageSize = (pageSize != null && pageSize > 0) ? pageSize : 20;
     * currentPageSize = Math.min(currentPageSize, 100);
     * return PageResult.of(items, total, currentPage, currentPageSize);
     *
     * // after
     * PaginationRequest req = PaginationRequest.of(page, pageSize);
     * return PageResult.of(items, total, req);
     * }</pre>
     *
     * @param items   the list of items
     * @param total   total number of items
     * @param request normalized pagination request
     * @param <T>     the type of items
     * @return PageResult instance
     */
    public static <T> PageResult<T> of(List<T> items, Long total, PaginationRequest request) {
        int totalPages = (int) Math.ceil((double) total / request.pageSize());
        return new PageResult<>(items, total, request.page(), request.pageSize(), totalPages);
    }

    /**
     * Same as {@link #of(List, Long, PaginationRequest)} but with an explicit
     * {@link DegradationStatus} marker for aggregated reads whose sources may
     * be partially or fully unavailable.
     *
     * @param items             the list of items
     * @param total             total number of items
     * @param request           normalized pagination request
     * @param degradationStatus explicit degradation marker ({@code null} treated as healthy)
     * @param <T>               the type of items
     * @return PageResult instance
     */
    public static <T> PageResult<T> of(
            List<T> items, Long total, PaginationRequest request, DegradationStatus degradationStatus) {
        PageResult<T> result = of(items, total, request);
        result.setDegradationStatus(degradationStatus);
        return result;
    }
}
