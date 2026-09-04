package com.ulticode.modules.search.source;

import com.ulticode.app.api.dto.ForumPostIndexDTO;
import com.ulticode.modules.forum.port.ForumPostReadPort;
import com.ulticode.modules.search.dto.SearchIndexType;
import com.ulticode.modules.search.dto.SearchResponseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Search source for the forum post domain.
 *
 * <p>P7-RELOCATE-FORUM-001: cutover from direct {@code ForumPostMapper}
 * to {@link ForumPostReadPort} after the forum family relocated to
 * {@code backend-app}. The port is defined in {@code backend-app-api}
 * which is already on the {@code backend-legacy} classpath.
 *
 * @author ulticode
 */
@Service
@RequiredArgsConstructor
public class ForumSearchSource implements SearchSource {

    private final ForumPostReadPort forumPostReadPort;

    @Override
    public SearchIndexType getIndexType() {
        return SearchIndexType.POSTS;
    }

    @Override
    public List<SearchResponseVO.SearchResultItem> searchDatabase(String query, int offset, int limit) {
        List<ForumPostIndexDTO> posts = forumPostReadPort.searchForIndex(query, offset, limit);

        List<SearchResponseVO.SearchResultItem> results = new ArrayList<>(posts.size());
        for (ForumPostIndexDTO post : posts) {
            results.add(SearchResponseVO.SearchResultItem.builder()
                    .id(post.id())
                    .type(SearchIndexType.POSTS.name())
                    .title(post.title())
                    .description(post.excerpt())
                    .url(buildUrl(post.id()))
                    .build());
        }
        return results;
    }

    @Override
    public long countDatabase(String query) {
        return forumPostReadPort.countForIndex(query);
    }

    @Override
    public String buildUrl(String entityId) {
        return "/forum/detailed/" + entityId;
    }
}
