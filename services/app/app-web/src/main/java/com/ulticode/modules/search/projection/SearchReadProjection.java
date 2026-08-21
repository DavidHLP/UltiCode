package com.ulticode.modules.search.projection;

import com.ulticode.modules.search.dto.SearchQueryDTO;
import com.ulticode.modules.search.dto.SearchResponseVO;

/**
 * Read-side deep module for the search domain. Owns every read that fans
 * out across the problems / users / posts / solutions indices: the
 * MeiliSearch backend (when configured), the database fallback (cross-table
 * LIKE joins), hit -&gt; {@link SearchResponseVO.SearchResultItem} view-shape
 * mapping, URL templating, and per-type metadata enrichment.
 *
 * <p>Replaces the deprecated {@code SearchService} facade. The controller is
 * the only caller and the default adapter is the only provider, so the seam
 * is real (same collapse pattern as {@code ForumReadProjection},
 * {@code UserReadProjection}, {@code ProblemProjection}).
 *
 * <p>The interface is intentionally narrow &mdash; two methods &mdash; because
 * the complexity lives behind it: four cross-module mapper dependencies, the
 * MeiliSearch SDK adapter, two independent backend paths, and the view-shape
 * aggregation that turns raw hits / entities into the search response VO.
 * The deletion test passes: deleting the interface would concentrate all of
 * that into the controller instead of removing it.
 *
 * @author ulticode
 */
public interface SearchReadProjection {

    /**
     * Search across all or a specific index. The configured read mode is
     * explicit: {@code DATABASE} never touches MeiliSearch; {@code INDEXED}
     * uses the event-backed index and only falls back when its explicit policy
     * allows it.
     *
     * @param queryDTO the search query parameters
     * @return the search response with results and explicit read semantics
     */
    SearchResponseVO search(SearchQueryDTO queryDTO);

    /**
     * Whether the MeiliSearch backend is wired in. Exposed so callers (and
     * health probes) can distinguish configured-with-engine from
     * database-only fallback.
     *
     * @return true if MeiliSearch is configured and available
     */
    boolean isMeiliSearchAvailable();
}
