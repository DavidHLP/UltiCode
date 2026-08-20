package com.ulticode.modules.forum.port;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ulticode.app.api.dto.ForumPostIndexDTO;
import com.ulticode.app.api.service.ForumPostReadPort;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * App-side adapter for {@link ForumPostReadPort}.
 *
 * <p>P7-LEAF-PLAN-001: fills the gap left by P7-RELOCATE-FORUM-001 —
 * the port was extracted when the forum family moved to backend-app,
 * but no implementation was ever provided in either module, so the
 * legacy search source had no backing bean in any Spring context.
 * Mirrors {@code DefaultProblemSearchReadPort}: non-deleted predicate,
 * title/excerpt LIKE match, limit enforcement.
 *
 * <p>Published semantics: the {@code forum_posts} table has no
 * {@code is_published} column; all existing forum queries scope by
 * {@code is_deleted = 0} only, so this adapter matches that contract.
 */
@Component
@RequiredArgsConstructor
public class DefaultForumPostReadAdapter implements ForumPostReadPort {

    private final ForumPostMapper forumPostMapper;

    @Override
    public List<ForumPostIndexDTO> searchForIndex(String query, int offset, int limit) {
        if (query == null || query.isBlank() || offset < 0 || limit <= 0) {
            return List.of();
        }
        QueryWrapper<ForumPost> wrapper = new QueryWrapper<>();
        wrapper.eq("is_deleted", false)
                .and(w -> w.like("title", query).or().like("excerpt", query))
                .orderByAsc("id")
                .last("LIMIT " + limit + " OFFSET " + offset);
        List<ForumPost> posts = forumPostMapper.selectList(wrapper);
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        return posts.stream()
                .map(p -> new ForumPostIndexDTO(
                        String.valueOf(p.getId()),
                        p.getTitle(),
                        p.getExcerpt(),
                        p.getPermalink()))
                .toList();
    }

    public List<ForumPostIndexDTO> searchForIndex(String query, int limit) {
        return searchForIndex(query, 0, limit);
    }

    @Override
    public long countForIndex(String query) {
        if (query == null || query.isBlank()) {
            return 0;
        }
        QueryWrapper<ForumPost> wrapper = new QueryWrapper<>();
        wrapper.eq("is_deleted", false)
                .and(w -> w.like("title", query).or().like("excerpt", query));
        Long count = forumPostMapper.selectCount(wrapper);
        return count == null ? 0 : count;
    }
}
