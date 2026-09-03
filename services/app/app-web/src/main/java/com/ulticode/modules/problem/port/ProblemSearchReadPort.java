package com.ulticode.modules.problem.port;

import com.ulticode.app.api.dto.ProblemIndexDTO;

import java.util.List;

/**
 * Read-side port for the Problem search source.
 *
 * <p>The provider owns published/non-deleted predicates, matching and limit
 * enforcement. A null/blank query or non-positive limit yields an empty list;
 * the result is never null.
 */
public interface ProblemSearchReadPort {

    /**
     * Search the Problem index by title or slug.
     *
     * @param query title/slug search text
     * @param limit maximum number of rows
     * @return matching index projections, never null
     */
    List<ProblemIndexDTO> searchForIndex(String query, int offset, int limit);

    default List<ProblemIndexDTO> searchForIndex(String query, int limit) {
        return searchForIndex(query, 0, limit);
    }

    long countForIndex(String query);
}
