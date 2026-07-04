package com.ulticode.modules.search.projection;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.model.Searchable;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.search.dto.SearchIndexType;
import com.ulticode.modules.search.dto.SearchQueryDTO;
import com.ulticode.modules.search.dto.SearchResponseVO;
import com.ulticode.modules.solution.entity.Solution;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default (and only) adapter for {@link SearchReadProjection}. Owns every
 * search read in one deep module &mdash; see the interface javadoc for why
 * the search read surface is a deep module rather than a thin service.
 *
 * <p>Logic moved verbatim from the deprecated {@code SearchServiceImpl}
 * facade. The facade is deleted (not retained as a delegate) because the
 * controller is the only caller, so the indirection was pure shallowness.
 * Every guard the facade used to inline is preserved here: the
 * MeiliSearch-optional setter injection (the {@link Client} bean is created
 * only when {@code meilisearch.enabled=true}), the per-index fan-out with
 * {@code perIndexLimit}, the broad-catch fallback to database LIKE queries
 * on any MeiliSearch failure, and the per-type URL / metadata templating.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultSearchReadProjection implements SearchReadProjection {

    private final ProblemMapper problemMapper;
    private final UserMapper userMapper;
    private final ForumPostMapper forumPostMapper;
    private final SolutionMapper solutionMapper;

    private Client meiliSearchClient;

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
     * Search using MeiliSearch.
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
     * Convert a MeiliSearch hit to SearchResultItem.
     */
    @SuppressWarnings("unchecked")
    private SearchResponseVO.SearchResultItem convertMeiliSearchHit(Map<String, Object> hit, SearchIndexType type) {
        String id = String.valueOf(hit.get("id"));
        String title = getStringValue(hit, "title", "name", "username");
        String description = getStringValue(hit, "summary", "excerpt", "content", "bio");
        String url = buildUrl(type, id, hit);

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
     * Search using database queries (fallback).
     */
    private SearchResponseVO searchWithDatabase(SearchQueryDTO queryDTO) {
        String query = queryDTO.getQuery().trim();
        SearchIndexType indexType = queryDTO.getIndex();
        int limit = queryDTO.getLimit();

        List<SearchResponseVO.SearchResultItem> results = new ArrayList<>();

        if (indexType == null || indexType == SearchIndexType.PROBLEMS) {
            results.addAll(searchProblems(query, indexType == null ? limit / 2 : limit));
        }

        if (indexType == null || indexType == SearchIndexType.USERS) {
            results.addAll(searchUsers(query, indexType == null ? limit / 4 : limit));
        }

        if (indexType == null || indexType == SearchIndexType.POSTS) {
            results.addAll(searchPosts(query, indexType == null ? limit / 4 : limit));
        }

        if (indexType == null || indexType == SearchIndexType.SOLUTIONS) {
            results.addAll(searchSolutions(query, indexType == null ? limit / 4 : limit));
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
     * Search problems by title or slug.
     */
    private List<SearchResponseVO.SearchResultItem> searchProblems(String query, int limit) {
        QueryWrapper<Problem> wrapper = new QueryWrapper<>();
        wrapper.and(w -> w
                .like("title", query)
                .or()
                .like("slug", query)
        )
                .eq("is_published", true)
                .eq("is_deleted", false)
                .last("LIMIT " + limit);

        List<Problem> problems = problemMapper.selectList(wrapper);

        List<SearchResponseVO.SearchResultItem> results = new ArrayList<>();
        for (Problem problem : problems) {
            results.add(SearchResponseVO.SearchResultItem.builder()
                    .id(String.valueOf(problem.getId()))
                    .type(SearchIndexType.PROBLEMS.name())
                    .title(problem.getTitle())
                    .description(problem.getSlug())
                    .url("/problems/" + problem.getSlug())
                    .metadata(createProblemMetadata(problem))
                    .build());
        }
        return results;
    }

    /**
     * Search users by username or name.
     */
    private List<SearchResponseVO.SearchResultItem> searchUsers(String query, int limit) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.and(w -> w
                .like("username", query)
                .or()
                .like("name", query)
        )
                .eq("is_active", true)
                .eq("is_banned", false)
                .eq("is_deleted", false)
                .last("LIMIT " + limit);

        List<User> users = userMapper.selectList(wrapper);

        List<SearchResponseVO.SearchResultItem> results = new ArrayList<>();
        for (User user : users) {
            results.add(SearchResponseVO.SearchResultItem.builder()
                    .id(user.getId())
                    .type(SearchIndexType.USERS.name())
                    .title(user.getUsername())
                    .description(user.getName() != null ? user.getName() : user.getUsername())
                    .url("/u/" + user.getUsername())
                    .metadata(createUserMetadata(user))
                    .build());
        }
        return results;
    }

    /**
     * Search forum posts by title or excerpt.
     */
    private List<SearchResponseVO.SearchResultItem> searchPosts(String query, int limit) {
        List<ForumPost> posts = forumPostMapper.searchPosts(query, limit);

        List<SearchResponseVO.SearchResultItem> results = new ArrayList<>();
        for (ForumPost post : posts) {
            results.add(SearchResponseVO.SearchResultItem.builder()
                    .id(post.getId())
                    .type(SearchIndexType.POSTS.name())
                    .title(post.getTitle())
                    .description(post.getExcerpt())
                    .url("/forum/post/" + post.getPermalink())
                    .build());
        }
        return results;
    }

    /**
     * Search solutions by title or summary.
     */
    private List<SearchResponseVO.SearchResultItem> searchSolutions(String query, int limit) {
        QueryWrapper<Solution> wrapper = new QueryWrapper<>();
        wrapper.and(w -> w
                .like("title", query)
                .or()
                .like("summary", query)
        )
                .eq("is_published", true)
                .eq("is_deleted", false)
                .last("LIMIT " + limit);

        List<Solution> solutions = solutionMapper.selectList(wrapper);

        List<SearchResponseVO.SearchResultItem> results = new ArrayList<>();
        for (Solution solution : solutions) {
            results.add(SearchResponseVO.SearchResultItem.builder()
                    .id(solution.getId())
                    .type(SearchIndexType.SOLUTIONS.name())
                    .title(solution.getTitle())
                    .description(solution.getSummary())
                    .url("/problems/" + solution.getProblemId() + "/solutions/" + solution.getId())
                    .build());
        }
        return results;
    }

    /**
     * Build URL for search result.
     */
    private String buildUrl(SearchIndexType type, String id, Map<String, Object> hit) {
        switch (type) {
            case PROBLEMS:
                String slug = hit.get("slug") != null ? hit.get("slug").toString() : id;
                return "/problems/" + slug;
            case USERS:
                String username = hit.get("username") != null ? hit.get("username").toString() : id;
                return "/u/" + username;
            case POSTS:
                String permalink = hit.get("permalink") != null ? hit.get("permalink").toString() : id;
                return "/forum/post/" + permalink;
            case SOLUTIONS:
                Object problemId = hit.get("problemId");
                if (problemId != null) {
                    return "/problems/" + problemId + "/solutions/" + id;
                }
                return "/solutions/" + id;
            default:
                return "/" + type.getIndexName() + "/" + id;
        }
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

    /**
     * Create metadata map for problem.
     */
    private Map<String, Object> createProblemMetadata(Problem problem) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("slug", problem.getSlug());
        if (problem.getDifficulty() != null) {
            metadata.put("difficulty", problem.getDifficulty());
        }
        return metadata;
    }

    /**
     * Create metadata map for user.
     */
    private Map<String, Object> createUserMetadata(User user) {
        Map<String, Object> metadata = new HashMap<>();
        if (user.getAvatar() != null) {
            metadata.put("avatar", user.getAvatar());
        }
        return metadata;
    }
}
