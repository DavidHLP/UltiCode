package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.ProblemListSummaryDTO;
import com.ulticode.common.response.PageResult;

/**
 * Owner read seam through which the Admin BFF paginates and filters the
 * problem-list catalog without importing the App-private problem-list
 * entities, mappers, or services.
 *
 * <p>Provider lives in {@code backend-app} (problemlist module) and owns
 * the query-wrapper assembly (search across name/description, featured /
 * public filters, sort selector, page normalization) and the entity →
 * summary DTO projection with per-list problem counts.
 */
public interface ProblemListSearchReadPort {

    /**
     * Paginated, filtered list of problem-list summary DTOs for the
     * management console.
     *
     * @param search     free-text search across name + description (null/blank = no filter)
     * @param isFeatured featured filter (null = no filter)
     * @param isPublic   public filter (null = no filter)
     * @param sortBy     sort field: {@code name} | {@code bannerOrder} | default {@code createdAt}
     * @param sortOrder  sort direction: {@code asc} | {@code desc} (default desc)
     * @param page       1-based page number
     * @param limit      page size
     * @return paged summary DTOs, never null
     */
    PageResult<ProblemListSummaryDTO> searchAdminLists(
            String search,
            Boolean isFeatured,
            Boolean isPublic,
            String sortBy,
            String sortOrder,
            int page,
            int limit);
}
