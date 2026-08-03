package com.ulticode.modules.search.source;

import com.ulticode.modules.search.dto.SearchIndexType;
import com.ulticode.modules.search.dto.SearchResponseVO;

import java.util.List;

/**
 * Per-source search contract. Each source domain (problem / user / post /
 * solution) owns its database search query, the entity → view-shape mapping,
 * and the URL template behind this single seam. The aggregator
 * ({@code DefaultSearchReadProjection}) only knows about the list of
 * {@code SearchSource} instances — it never reaches into a source's mapper,
 * its columns, or its URL format.
 *
 * <p>Three responsibilities, one interface:
 * <ol>
 *   <li>{@link #getIndexType()} — the index the source serves, used by the
 *       aggregator to route per-type searches.</li>
 *   <li>{@link #searchDatabase(String, int, int)} — the source's own database
 *       LIKE query and row → {@code SearchResultItem} projection. The mapper,
 *       the columns (e.g. {@code is_published}, {@code is_deleted}), and the
 *       metadata keys are all owned by the source.</li>
 *   <li>{@link #buildUrl(String)} — the source's URL template, used as the
 *       canonical / fallback URL by both the aggregator and any non-database
 *       (e.g. MeiliSearch) code path.</li>
 * </ol>
 *
 * <p>The interface is intentionally narrow — three methods — because the
 * cross-module mappers and the column-name knowledge live in the
 * implementations. Replacing this seam with a different transport (vector
 * search, dedicated read replica, etc.) only requires adding another
 * implementation; the aggregator does not change.
 *
 * @author ulticode
 */
public interface SearchSource {

    /**
     * The index this source serves. Used by the aggregator to look up the
     * source in its per-type routing table.
     *
     * @return the search index type owned by this source
     */
    SearchIndexType getIndexType();

    /**
     * Run a database search for this source and project the matching rows
     * into {@link SearchResponseVO.SearchResultItem} entries. The source
     * owns its own query, mapper, and per-row metadata.
     *
     * <p>The {@code offset} is passed through for forward compatibility; the
     * source applies its own {@code LIMIT} derived from {@code limit}.
     *
     * @param query the trimmed user query (already trimmed by the caller)
     * @param offset the offset returned to the caller (informational)
     * @param limit the maximum rows to return
     * @return the matched rows projected into SearchResultItem, never null
     */
    List<SearchResponseVO.SearchResultItem> searchDatabase(String query, int offset, int limit);

    /**
     * Build the frontend URL for a single entity of this source by its
     * natural identifier (slug for problems, username for users, permalink
     * for posts, id for solutions). Each source owns its URL template so
     * the search module never needs to know about per-domain permalink
     * formats.
     *
     * @param entityId the entity's natural identifier
     * @return the relative URL
     */
    String buildUrl(String entityId);
}