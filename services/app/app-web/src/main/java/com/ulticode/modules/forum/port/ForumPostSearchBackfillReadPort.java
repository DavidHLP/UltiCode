package com.ulticode.modules.forum.port;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import com.ulticode.modules.search.backfill.SearchBackfillDocument;
import com.ulticode.modules.search.backfill.SearchBackfillReadPort;
import com.ulticode.modules.search.dto.SearchIndexType;
import com.ulticode.modules.search.source.SearchDocumentBuilders;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SEARCH-003 forum post backfill enumeration (DEC-017).
 *
 * <p>Predicate mirrors the Q-read seam ({@code DefaultForumPostReadAdapter}:
 * non-deleted). Version is the row's {@code updated_at} epoch millis
 * (V20260816220000 added the column with MySQL ON UPDATE).
 */
@Component
@RequiredArgsConstructor
public class ForumPostSearchBackfillReadPort implements SearchBackfillReadPort {

    private final ForumPostMapper forumPostMapper;

    @Override
    public SearchIndexType type() {
        return SearchIndexType.POSTS;
    }

    @Override
    public List<SearchBackfillDocument> enumerateForBackfill(int offset, int limit) {
        QueryWrapper<ForumPost> wrapper = new QueryWrapper<>();
        wrapper.select("id", "title", "excerpt", "permalink", "updated_at")
                .eq("is_deleted", false)
                .orderByAsc("id")
                .last("LIMIT " + limit + " OFFSET " + offset);
        return forumPostMapper.selectList(wrapper).stream()
                .map(p -> new SearchBackfillDocument(
                        p.getId(),
                        SearchBackfillReadPort.toVersionMillis(p.getUpdatedAt()),
                        SearchDocumentBuilders.forumPost(p)))
                .toList();
    }
}
