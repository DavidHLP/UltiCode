package com.ulticode.common.response;

import lombok.Data;

import java.util.List;

/**
 * Paginated response wrapper for list data.
 * Matches NestJS pagination format exactly for frontend compatibility.
 *
 * @param <T> the type of items in the page
 */
@Data
public class PageResult<T> {

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
}
