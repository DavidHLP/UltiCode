package com.ulticode.modules.problem.port;

import com.ulticode.app.api.dto.ProblemListItemDTO;

import java.util.Collection;
import java.util.List;

/**
 * Read-side port for Problem projections consumed by problem-list pages.
 *
 * <p>The list provider owns relation ordering; this port returns only the
 * Problem-owned item data. A {@code null} or empty request returns an empty
 * list, missing Problem rows are omitted, and the result itself is never
 * {@code null}.
 */
public interface ProblemListReadPort {

    /**
     * Batch-load Problem items for the supplied numeric identifiers.
     *
     * @param problemIds Problem IDs; null/empty means no results
     * @return found items, never null
     */
    List<ProblemListItemDTO> findByIds(Collection<Long> problemIds);
}
