package com.ulticode.modules.search.source;

import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import com.ulticode.modules.search.dto.SearchIndexType;
import com.ulticode.modules.search.dto.SearchResponseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Search source for the forum post domain. Owns:
 * <ul>
 *   <li>The {@link ForumPostMapper#searchPosts} call (the mapper owns the
 *       {@code is_deleted} predicate and the title / excerpt LIKE
 *       matching) and the LIMIT cap.</li>
 *   <li>The {@code /forum/post/{permalink}} URL template.</li>
 * </ul>
 *
 * <p>Forum posts carry no metadata in the search response (the title and
 * excerpt already encode the rankable surface), so this source is the
 * leanest of the four.
 *
 * @author ulticode
 */
@Service
@RequiredArgsConstructor
public class ForumSearchSource implements SearchSource {

    private final ForumPostMapper forumPostMapper;

    @Override
    public SearchIndexType getIndexType() {
        return SearchIndexType.POSTS;
    }

    @Override
    public List<SearchResponseVO.SearchResultItem> searchDatabase(String query, int offset, int limit) {
        List<ForumPost> posts = forumPostMapper.searchPosts(query, limit);

        List<SearchResponseVO.SearchResultItem> results = new ArrayList<>(posts.size());
        for (ForumPost post : posts) {
            results.add(SearchResponseVO.SearchResultItem.builder()
                    .id(post.getId())
                    .type(SearchIndexType.POSTS.name())
                    .title(post.getTitle())
                    .description(post.getExcerpt())
                    .url(buildUrl(post.getPermalink()))
                    .build());
        }
        return results;
    }

    @Override
    public String buildUrl(String entityId) {
        return "/forum/post/" + entityId;
    }
}