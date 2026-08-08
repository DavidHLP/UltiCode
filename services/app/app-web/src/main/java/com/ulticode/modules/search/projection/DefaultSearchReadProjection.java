package com.ulticode.modules.search.projection;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.model.Searchable;
import com.ulticode.modules.search.dto.SearchIndexType;
import com.ulticode.modules.search.dto.SearchQueryDTO;
import com.ulticode.modules.search.dto.SearchResponseVO;
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
 * only caller, so the indirection was pure shallowness. Every guard the
 * facade used to inline is preserved here: the MeiliSearch-optional setter
 * injection (the {@link Client} bean is created only when
 * {@code meilisearch.enabled=true}), the per-index fan-out with
 * {@code perIndexLimit}, the broad-catch fallback to database queries on
 * any MeiliSearch failure, and the per-type metadata enrichment for
 * MeiliSearch hits.
 *
 * @author ulticode
 */
@Slf4j
@Service
public class DefaultSearchReadProjection implements SearchReadProjection {

    private final List<SearchSource> sources;
    private final Map<SearchIndexType, SearchSource> sourcesByType;

    private Client meiliSearchClient;

    /**
     * Spring auto-injects every {@link SearchSource} bean into this list.
     */
    public DefaultSearchReadProjection(List<SearchSource> sources) {
        this.sources = List.copyOf(sources);
        Map<SearchIndexType, SearchSource> map = new EnumMap<>(SearchIndexType.class);
        for (SearchSource source : sources) {
            map.put(source.getIndexType(), source);
        }
        this.sourcesByType = Collections.unmodifiableMap(map);
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

        // Try MeiliSearch first
        if (isMeiliSearchAvailable()) {
            try {
                return searchWithMeiliSearch(queryDTO);
            // broad catch: fallback to database search on MeiliSearch failure
            } catch (Exception e) {
                log.warn("MeiliSearch search failed, falling back to database: {}", e.getMessage());
            }
        }

        // Fallback to database search
        return searchWithDatabase(queryDTO);
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
        int offset = queryDTO.getOffset();

        List<SearchResponseVO.SearchResultItem> results = new ArrayList<>();
        long totalHits = 0;

        if (indexType != null) {
            // Search in specific index
            SearchResponseVO.SearchResultItem[] items = searchIndex(indexType, query, limit, offset);
            for (SearchResponseVO.SearchResultItem item : items) {
                results.add(item);
                totalHits++;
            }
        } else {
            // Search all indices
            int perIndexLimit = Math.max(5, limit / 4);
            for (SearchIndexType type : SearchIndexType.values()) {
                SearchResponseVO.SearchResultItem[] items = searchIndex(type, query, perIndexLimit, 0);
                for (SearchResponseVO.SearchResultItem item : items) {
                    results.add(item);
                    totalHits++;
                }
            }
            // Limit total results
            if (results.size() > limit) {
                results = results.subList(0, limit);
            }
        }

        return SearchResponseVO.builder()
                .query(queryDTO.getQuery())
                .total(totalHits)
                .page(queryDTO.getPage())
                .limit(limit)
                .results(results)
                .build();
    }

    /**
     * Search a specific MeiliSearch index.
     */
    private SearchResponseVO.SearchResultItem[] searchIndex(SearchIndexType indexType, String query, int limit, int offset) {
        try {
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

            return items.toArray(new SearchResponseVO.SearchResultItem[0]);
        // broad catch: fallback to database search on MeiliSearch failure
        } catch (Exception e) {
            log.error("Error searching MeiliSearch index {}: {}", indexType.getIndexName(), e.getMessage());
            return new SearchResponseVO.SearchResultItem[0];
        }
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
     * hit may carry a richer identifier (problem slug, username, post
     * permalink) that should be preferred over the raw id when available.
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
                String permalink = hit.get("permalink") != null ? hit.get("permalink").toString() : null;
                return source.buildUrl(permalink != null ? permalink : id);
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
    private SearchResponseVO searchWithDatabase(SearchQueryDTO queryDTO) {
        String query = queryDTO.getQuery().trim();
        SearchIndexType indexType = queryDTO.getIndex();
        int limit = queryDTO.getLimit();

        List<SearchResponseVO.SearchResultItem> results = new ArrayList<>();

        if (indexType == null) {
            // Aggregate across all sources with per-source budget
            int problemBudget = limit / 2;
            int others = limit / 4;
            for (SearchSource source : sources) {
                SearchIndexType type = source.getIndexType();
                int budget = (type == SearchIndexType.PROBLEMS) ? problemBudget : others;
                results.addAll(source.searchDatabase(query, 0, budget));
            }
        } else {
            // Route to the specific source
            SearchSource source = sourcesByType.get(indexType);
            if (source != null) {
                results.addAll(source.searchDatabase(query, 0, limit));
            }
        }

        // Limit total results
        if (results.size() > limit) {
            results = results.subList(0, limit);
        }

        return SearchResponseVO.builder()
                .query(queryDTO.getQuery())
                .total(results.size())
                .page(queryDTO.getPage())
                .limit(limit)
                .results(results)
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