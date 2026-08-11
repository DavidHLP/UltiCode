package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.SolutionIndexDTO;

import java.util.List;

/**
 * Read-side port for solution queries owned by the App service.
 *
 * <p>Consumed by legacy modules (search, problem) that previously imported
 * {@code SolutionMapper} directly. This port returns primitive types or
 * DTOs — never the internal {@code Solution} entity.
 *
 * <p>P7-RELOCATE-SOLUTION-001: extracted when the solution family relocated
 * from backend-legacy to backend-app.
 */
public interface SolutionReadPort {

    /**
     * Search published solutions by title or summary LIKE match.
     *
     * <p>Only published, non-deleted solutions are returned. The query
     * matches {@code title} or {@code summary} with a LIKE pattern.
     *
     * @param query search query string
     * @param limit maximum results
     * @return list of matching solutions as index DTOs
     */
    List<SolutionIndexDTO> searchForIndex(String query, int limit);

    /**
     * Count published solutions for a given problem.
     *
     * @param problemId parent problem ID
     * @return count of solutions for the problem
     */
    long countByProblemId(Long problemId);

    /**
     * Count published solutions for a given user.
     *
     * @param userId user ID
     * @return count of solutions authored by the user
     */
    long countByUserId(String userId);

    /**
     * Batch-load solution titles for the given solution ids.
     *
     * <p>Consumed by the Admin service's comment enrichment
     * ({@code AdminCommentReadAdapter}, ADMIN-006) which previously imported
     * {@code SolutionMapper} directly. Batch {@code selectBatchIds}
     * semantics: logical-deleted rows are excluded.
     *
     * @param solutionIds candidate solution IDs
     * @return map keyed by solution id; ids with no matching solution are
     *         absent from the map. Empty input returns an empty map.
     */
    java.util.Map<String, String> findTitlesByIds(java.util.Set<String> solutionIds);
}
