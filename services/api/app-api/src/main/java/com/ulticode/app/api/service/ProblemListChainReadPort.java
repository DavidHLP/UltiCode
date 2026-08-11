package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.ProblemListDetailDTO;
import com.ulticode.app.api.dto.ProblemListSummaryDTO;

/**
 * Owner read seam through which the Admin BFF loads a single problem
 * list and its ordered problem chain (the list → relations → problems
 * hierarchy) without importing the App-private problem-list entities,
 * mappers, or services.
 *
 * <p>Provider lives in {@code backend-app} (problemlist module) and
 * executes every query inside the App owner. Non-throwing contract:
 * single-row lookups return {@code null} for a missing list; the Admin
 * edge maps {@code null} to its own 404 semantics.
 */
public interface ProblemListChainReadPort {

    /**
     * Load the list-owned summary row (no problem chain). Used by the
     * Admin write paths for pre-state audit snapshots and 404 detection.
     *
     * @param listId the list ID
     * @return the summary DTO, or {@code null} when the list is missing
     */
    ProblemListSummaryDTO findSummary(String listId);

    /**
     * Load the list detail together with its ordered problem chain
     * (relations joined with Problem-owned item columns and tags).
     * Used by the Admin detail read.
     *
     * @param listId the list ID
     * @return the detail DTO, or {@code null} when the list is missing
     */
    ProblemListDetailDTO findAdminDetail(String listId);
}
