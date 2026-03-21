package com.ulticode.modules.search.service;

import com.ulticode.modules.search.dto.SearchQueryDTO;
import com.ulticode.modules.search.dto.SearchResponseVO;

/**
 * Service for full-text search operations.
 * Supports MeiliSearch for fast full-text search with database fallback.
 */
public interface SearchService {

    /**
     * Search across all or specific indices.
     *
     * @param queryDTO the search query parameters
     * @return the search response with results
     */
    SearchResponseVO search(SearchQueryDTO queryDTO);

    /**
     * Check if MeiliSearch is available.
     *
     * @return true if MeiliSearch is configured and available
     */
    boolean isMeiliSearchAvailable();
}
