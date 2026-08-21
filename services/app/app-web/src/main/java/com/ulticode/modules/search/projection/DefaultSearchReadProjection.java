package com.ulticode.modules.search.projection;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.model.SearchResultPaginated;
import com.meilisearch.sdk.model.Searchable;
import com.ulticode.modules.search.dto.SearchIndexType;
import com.ulticode.modules.search.dto.SearchQueryDTO;
import com.ulticode.modules.search.dto.SearchResponseVO;
import com.ulticode.modules.search.dto.SearchReadSemantics;
import com.ulticode.modules.search.config.SearchReadProperties;
import com.ulticode.modules.search.source.SearchSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregator over the per-source {@link SearchSource} adapters. The
 * database-path search logic and the per-source URL templates live in the
 * individual adapters — this class only knows about the {@link SearchSource}
 * seam, the MeiliSearch backend (an infrastructure concern that legitimately
 * belongs here because MeiliSearch is shared across all sources and has no
 * per-source database mapper), and the response aggregation / truncation.
 *
 * <p>Replaces the deprecated {@code SearchServiceImpl} facade. The facade
 * is deleted (not retained as a delegate) because the controller is the
 * only caller, so the indirection was pure shallowness. The read mode is
 * explicit: database mode stays deterministic, while indexed mode owns
 * MeiliSearch totals, fixed-order page mapping, optional fallback, and
 * per-type metadata enrichment. The {@link Client} bean remains optional
 * and is created only when {@code meilisearch.enabled=true}.
 *
 * @author ulticode
 */
@Slf4j
@Service
public class DefaultSearchReadProjection implements SearchReadProjection {

    private final List<SearchSource> sources;
    private final Map<SearchIndexType, SearchSource> sourcesByType;
    private final SearchReadProperties readProperties;

    private Client meiliSearchClient;

    /**
     * Spring auto-injects every {@link SearchSource} bean into this list.
     */
    @Autowired
    public DefaultSearchReadProjection(List<SearchSource> sources, SearchReadProperties readProperties) {
        this.sources = List.copyOf(sources);
        this.readProperties = readProperties;
        Map<SearchIndexType, SearchSource> map = new EnumMap<>(SearchIndexType.class);
        for (SearchSource source : sources) {
            map.put(source.getIndexType(), source);
        }
        this.sourcesByType = Collections.unmodifiableMap(map);
    }

    public DefaultSearchReadProjection(List<SearchSource> sources) {
        this(sources, new SearchReadProperties());
    }

    /** Test seam for selecting a read mode without booting a Spring context. */
    void setReadMode(SearchReadProperties.Mode mode) {
        readProperties.setMode(mode);
    }

    /** Test seam for making fallback policy explicit in focused tests. */
    void setFallbackToDatabase(boolean fallbackToDatabase) {
        readProperties.setFallbackToDatabase(fallbackToDatabase);
    }

    /** Test seam for the optional DevStack-published worker readiness flag. */
    void setWorkerEnabled(Boolean workerEnabled) {
        readProperties.setWorkerEnabled(workerEnabled);
    }

    @Autowired(required = false)
    public void setMeiliSearchClient(Client meiliSearchClient) {
        this.meiliSearchClient = meiliSearchClient;
    }

    @Override
    public SearchResponseVO search(SearchQueryDTO queryDTO) {
        String query = queryDTO.getQuery().trim();
        int limit = queryDTO.getLimit();
        int offset = queryDTO.getOffset();

        log.debug("Searching for: {} with limit: {} and offset: {}", query, limit, offset);

        if (readProperties.getMode() == SearchReadProperties.Mode.DATABASE) {
            return searchWithDatabase(queryDTO, false);
        }

        if (readProperties.getMode() != SearchReadProperties.Mode.INDEXED) {
            throw new IllegalStateException("Unsupported search read mode: " + readProperties.getMode());
        }
        if (!Boolean.TRUE.equals(readProperties.getWorkerEnabled())) {
            return indexedUnavailable(queryDTO,
                    new IllegalStateException("Search worker is not explicitly enabled"));
        }
        if (!isMeiliSearchAvailable()) {
            return indexedUnavailable(queryDTO, null);
        }
        try {
            return searchWithMeiliSearch(queryDTO);
        } catch (Exception e) {
            return indexedUnavailable(queryDTO, e);
        }
    }

    private SearchResponseVO indexedUnavailable(SearchQueryDTO queryDTO, Exception cause) {
        if (readProperties.isFallbackToDatabase()) {
            log.warn("Indexed search unavailable, falling back to database: {}",
                    cause == null ? "MeiliSearch client is not configured" : cause.getMessage());
            return searchWithDatabase(queryDTO, true);
        }
        throw new IllegalStateException("Indexed search is enabled but MeiliSearch is unavailable", cause);
    }

    @Override
    public boolean isMeiliSearchAvailable() {
        return meiliSearchClient != null;
    }

    /**
     * Search using MeiliSearch. MeiliSearch is a single infrastructure
     * backend that indexes all sources; it does not reach into the
     * per-source mappers, so it stays on the aggregator. URL templating
     * for each hit is delegated back to the owning source.
     */
    private SearchResponseVO searchWithMeiliSearch(SearchQueryDTO queryDTO) {
        String query = queryDTO.getQuery().trim();
        SearchIndexType indexType = queryDTO.getIndex();
        int limit = queryDTO.getLimit();
        int offset = Math.max(queryDTO.getOffset(), 0);

        List<SearchResponseVO.SearchResultItem> results = new ArrayList<>();
        long totalHits = 0;

        if (indexType != null) {
            totalHits = exactTotal(indexType, query);
            if (offset < totalHits) {
                results.addAll(searchIndex(indexType, query, limit, offset));
            }
        } else {
            Map<SearchIndexType, Long> totals = new EnumMap<>(SearchIndexType.class);
            for (SearchIndexType type : SearchIndexType.values()) {
                long total = exactTotal(type, query);
                totals.put(type, total);
                totalHits += total;
            }
            long remainingOffset = offset;
            int remainingLimit = limit;
            for (SearchIndexType type : SearchIndexType.values()) {
                if (remainingLimit == 0) {
                    break;
                }
                long typeTotal = totals.get(type);
                if (remainingOffset >= typeTotal) {
                    remainingOffset -= typeTotal;
                    continue;
                }
                List<SearchResponseVO.SearchResultItem> items = searchIndex(
                        type, query, remainingLimit, Math.toIntExact(remainingOffset));
                int accepted = Math.min(items.size(), remainingLimit);
                results.addAll(items.subList(0, accepted));
                remainingLimit -= accepted;
                remainingOffset = 0;
            }
        }

        return SearchResponseVO.builder()
                .query(queryDTO.getQuery())
                .total(totalHits)
                .page(queryDTO.getPage())
                .limit(limit)
                .results(results)
                .semantics(new SearchReadSemantics(
                        "INDEXED", "MEILISEARCH", "EVENTUAL",
                        "MEILI_RELEVANCE_THEN_INDEX_ORDER",
                        "EXACT_UNDER_MAX_TOTAL_HITS", false))
                .build();
    }

    /**
     * Search a specific MeiliSearch index.
     */
    private List<SearchResponseVO.SearchResultItem> searchIndex(
            SearchIndexType indexType, String query, int limit, int offset) {
        Index index = meiliSearchClient.index(indexType.getIndexName());
        SearchRequest searchRequest = SearchRequest.builder()
                .q(query)
                .limit(limit)
                .offset(offset)
                .attributesToHighlight(new String[]{"title", "summary", "excerpt", "content", "name", "username", "bio"})
                .build();

        Searchable searchResult = index.search(searchRequest);
        List<SearchResponseVO.SearchResultItem> items = new ArrayList<>();
        if (searchResult.getHits() != null) {
            for (Object hit : searchResult.getHits()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> hitMap = (Map<String, Object>) hit;
                items.add(convertMeiliSearchHit(hitMap, indexType));
            }
        }
        return items;
    }

    private long exactTotal(SearchIndexType indexType, String query) {
        Index index = meiliSearchClient.index(indexType.getIndexName());
        Searchable countResult = index.search(SearchRequest.builder()
                .q(query)
                .page(1)
                .hitsPerPage(0)
                .build());
        return exactTotal(index, countResult);
    }

    private long exactTotal(Index index, Searchable searchResult) {
        if (!(searchResult instanceof SearchResultPaginated result)) {
            throw new IllegalStateException("MeiliSearch response did not include an exhaustive total");
        }
        int maxTotalHits = index.getPaginationSettings().getMaxTotalHits();
        if (maxTotalHits <= 0 || result.getTotalHits() >= maxTotalHits) {
            throw new IllegalStateException("MeiliSearch total reached pagination.maxTotalHits");
        }
        return result.getTotalHits();
    }

    /**
     * Convert a MeiliSearch hit to SearchResultItem. Per-source URL
     * templating is delegated to the owning source; per-source metadata
     * (problem difficulty / slug, user avatar) is extracted here because
     * MeiliSearch hits are a flat string-keyed map that has no
     * domain-specific type.
     */
    @SuppressWarnings("unchecked")
    private SearchResponseVO.SearchResultItem convertMeiliSearchHit(Map<String, Object> hit, SearchIndexType type) {
        String id = String.valueOf(hit.get("id"));
        String title = getStringValue(hit, "title", "name", "username");
        String description = getStringValue(hit, "summary", "excerpt", "content", "bio");
        String url = resolveMeiliUrl(type, id, hit);

        // Extract highlights
        Map<String, List<String>> highlights = new HashMap<>();
        Object formatted = hit.get("_formatted");
        if (formatted instanceof Map) {
            Map<String, Object> formattedMap = (Map<String, Object>) formatted;
            extractHighlights(formattedMap, highlights, "title", "summary", "excerpt", "content", "name", "username", "bio");
        }

        // Build metadata
        Map<String, Object> metadata = new HashMap<>();
        if (type == SearchIndexType.PROBLEMS) {
            if (hit.get("difficulty") != null) {
                metadata.put("difficulty", hit.get("difficulty"));
            }
            if (hit.get("slug") != null) {
                metadata.put("slug", hit.get("slug"));
            }
        } else if (type == SearchIndexType.USERS) {
            if (hit.get("avatar") != null) {
                metadata.put("avatar", hit.get("avatar"));
            }
        }

        return SearchResponseVO.SearchResultItem.builder()
                .id(id)
                .type(type.name())
                .title(title)
                .description(description)
                .url(url)
                .highlights(highlights.isEmpty() ? null : highlights)
                .metadata(metadata.isEmpty() ? null : metadata)
                .build();
    }

    /**
     * Resolve the URL for a MeiliSearch hit. The owning source owns the
     * URL template via {@link SearchSource#buildUrl(String)}; the per-source
     * hit may carry a richer identifier (problem slug or username) that
     * should be preferred over the raw id when the URL contract supports it.
     */
    private String resolveMeiliUrl(SearchIndexType type, String id, Map<String, Object> hit) {
        SearchSource source = sourcesByType.get(type);
        if (source == null) {
            return "/" + type.getIndexName() + "/" + id;
        }
        switch (type) {
            case PROBLEMS: {
                String slug = hit.get("slug") != null ? hit.get("slug").toString() : null;
                return source.buildUrl(slug != null ? slug : id);
            }
            case USERS: {
                String username = hit.get("username") != null ? hit.get("username").toString() : null;
                return source.buildUrl(username != null ? username : id);
            }
            case POSTS: {
                return source.buildUrl(id);
            }
            case SOLUTIONS: {
                Object problemId = hit.get("problemId");
                if (problemId != null) {
                    return "/problems/" + problemId + "/solutions/" + id;
                }
                return source.buildUrl(id);
            }
            default:
                return "/" + type.getIndexName() + "/" + id;
        }
    }

    /**
     * Extract highlights from formatted result.
     */
    private void extractHighlights(Map<String, Object> formatted, Map<String, List<String>> highlights, String... fields) {
        for (String field : fields) {
            Object value = formatted.get(field);
            if (value != null) {
                String strValue = value.toString();
                if (strValue.contains("<em>")) {
                    highlights.put(field, List.of(strValue));
                }
            }
        }
    }

    /**
     * Search the database via the per-source adapters. Each source owns
     * its own mapper, columns, and per-row metadata; the aggregator only
     * routes the call and aggregates / truncates the response.
     */
    private SearchResponseVO searchWithDatabase(SearchQueryDTO queryDTO, boolean fallbackApplied) {
        String query = queryDTO.getQuery().trim();
        SearchIndexType indexType = queryDTO.getIndex();
        int limit = queryDTO.getLimit();
        int offset = Math.max(queryDTO.getOffset(), 0);

        List<SearchResponseVO.SearchResultItem> results = new ArrayList<>();
        long total = 0;

        if (indexType != null) {
            // Route to the specific source
            SearchSource source = sourcesByType.get(indexType);
            if (source != null) {
                total = source.countDatabase(query);
                if (offset < total) {
                    List<SearchResponseVO.SearchResultItem> page =
                            source.searchDatabase(query, offset, limit);
                    results.addAll(page.subList(0, Math.min(page.size(), limit)));
                }
            }
        } else {
            int remainingOffset = offset;
            int remainingLimit = limit;
            for (SearchIndexType type : SearchIndexType.values()) {
                SearchSource source = sourcesByType.get(type);
                if (source == null) {
                    continue;
                }
                long sourceTotal = source.countDatabase(query);
                total += sourceTotal;
                if (remainingLimit == 0) {
                    continue;
                }
                if (remainingOffset >= sourceTotal) {
                    remainingOffset -= (int) sourceTotal;
                    continue;
                }
                List<SearchResponseVO.SearchResultItem> page =
                        source.searchDatabase(query, remainingOffset, remainingLimit);
                int accepted = Math.min(page.size(), remainingLimit);
                results.addAll(page.subList(0, accepted));
                remainingLimit -= accepted;
                remainingOffset = 0;
            }
        }

        return SearchResponseVO.builder()
                .query(queryDTO.getQuery())
                .total(total)
                .page(queryDTO.getPage())
                .limit(limit)
                .results(results)
                .semantics(new SearchReadSemantics(
                        fallbackApplied ? "INDEXED" : "DATABASE",
                        "DATABASE", "REALTIME", "SOURCE_ID_ASC", "EXACT", fallbackApplied))
                .build();
    }

    /**
     * Get string value from map with fallback keys.
     */
    private String getStringValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !value.toString().isEmpty()) {
                return value.toString();
            }
        }
        return "";
    }

}
