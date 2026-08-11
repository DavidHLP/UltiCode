package com.ulticode.modules.forum.port;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.app.api.dto.AdminForumCommunityDTO;
import com.ulticode.app.api.dto.AdminForumCommunityPage;
import com.ulticode.app.api.dto.AdminForumPostPage;
import com.ulticode.app.api.dto.AdminForumPostQuery;
import com.ulticode.app.api.dto.AdminForumPostRowDTO;
import com.ulticode.app.api.service.AdminForumReadPort;
import com.ulticode.modules.forum.entity.ForumCommunity;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumCommentMapper;
import com.ulticode.modules.forum.mapper.ForumCommunityMapper;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ADMIN-007: provider implementing {@link AdminForumReadPort} inside
 * {@code backend-app} (forum implementation module) so the Admin service
 * reads forum posts / communities without importing the entities or
 * mappers.
 *
 * <p>Reproduces the pre-migration admin read semantics exactly: search
 * matches title OR excerpt, all optional state filters are exact
 * matches, sort defaults to {@code createdAt} desc, communities are
 * ordered by member count desc, and comment counts are batch-loaded per
 * page. {@code commentCount} ordering is evaluated by the App-owned SQL
 * query before pagination, with created-at/id tie-breaks. Vote counts are
 * intentionally NOT computed here — they belong to the vote module's
 * {@code ForumPostVoteCountReadPort} and are composed by the Admin consumer.
 *
 * @author ulticode
 */
@Component
@Primary
@RequiredArgsConstructor
public class DefaultAdminForumReadAdapter implements AdminForumReadPort {
    private static final int MAX_PAGE_SIZE = 100;

    private final ForumPostMapper forumPostMapper;
    private final ForumCommentMapper forumCommentMapper;
    private final ForumCommunityMapper forumCommunityMapper;

    @Override
    public AdminForumPostPage listPosts(AdminForumPostQuery query) {
        int page = query.page() > 0 ? query.page() : 1;
        int limit = query.limit() > 0 ? Math.min(query.limit(), MAX_PAGE_SIZE) : 20;
        Page<ForumPost> pageResult = new Page<>(page, limit);
        List<ForumPost> records = forumPostMapper.selectPageIgnoreDeleted(
                pageResult,
                query.search(),
                query.communityId(),
                query.authorId(),
                query.isFlagged(),
                query.isPinned(),
                query.isLocked(),
                query.isDeleted(),
                query.sortBy(),
                query.sortOrder());
        records = records != null ? records : List.of();
        pageResult.setRecords(records);

        List<String> postIds = records.stream().map(ForumPost::getId).toList();
        Map<String, Long> commentCountMap = commentCountsByPostIds(postIds);
        Map<String, ForumCommunity> communityMap = communitiesByIds(
                records.stream().map(ForumPost::getCommunityId).collect(Collectors.toSet()));
        List<AdminForumPostRowDTO> rows = records.stream()
                .map(p -> toRow(p, commentCountMap.getOrDefault(p.getId(), 0L), communityMap.get(p.getCommunityId())))
                .toList();
        return new AdminForumPostPage(rows, pageResult.getTotal());
    }

    @Override
    public AdminForumPostRowDTO getPost(String postId) {
        ForumPost post = forumPostMapper.selectByIdIgnoreDeleted(postId);
        if (post == null) {
            return null;
        }
        AdminForumPostRowDTO row = toRow(post, forumCommentMapper.countByPostId(postId),
                forumCommunityMapper.selectById(post.getCommunityId()));
        // Detail view mirrors the excerpt as content (matches the
        // pre-migration VO behavior — the forum entity has no separate
        // content column).
        row.setContent(post.getExcerpt());
        return row;
    }

    @Override
    public AdminForumCommunityPage listCommunities(int page, int limit, String search) {
        LambdaQueryWrapper<ForumCommunity> wrapper = new LambdaQueryWrapper<ForumCommunity>()
                .orderByDesc(ForumCommunity::getMembers);

        if (StringUtils.hasText(search)) {
            String like = "%" + search.trim() + "%";
            wrapper.and(w -> w
                    .like(ForumCommunity::getName, like)
                    .or()
                    .like(ForumCommunity::getSlug, like)
                    .or()
                    .like(ForumCommunity::getDescription, like));
        }

        Page<ForumCommunity> pageResult = new Page<>(page, limit);
        Page<ForumCommunity> result = forumCommunityMapper.selectPage(pageResult, wrapper);

        List<AdminForumCommunityDTO> rows = result.getRecords().stream()
                .map(c -> new AdminForumCommunityDTO(
                        c.getId(),
                        c.getName(),
                        c.getSlug(),
                        c.getDescription(),
                        c.getPostsCount() != null ? c.getPostsCount() : 0,
                        c.getMembers() != null ? c.getMembers() : 0))
                .toList();

        return new AdminForumCommunityPage(rows, result.getTotal());
    }

    @Override
    public Map<String, String> findPostTitlesByIds(Collection<String> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new HashMap<>();
        for (ForumPost post : forumPostMapper.selectBatchIds(postIds)) {
            result.put(post.getId(), post.getTitle());
        }
        return result;
    }

    private Map<String, Long> commentCountsByPostIds(List<String> postIds) {
        if (postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Long> map = new HashMap<>();
        forumCommentMapper.countByPostIds(postIds)
                .forEach(row -> map.put((String) row.get("post_id"), ((Number) row.get("cnt")).longValue()));
        return map;
    }

    private Map<String, ForumCommunity> communitiesByIds(Collection<String> communityIds) {
        if (communityIds == null || communityIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return forumCommunityMapper.selectBatchIds(communityIds).stream()
                .collect(Collectors.toMap(ForumCommunity::getId, c -> c));
    }

    private AdminForumPostRowDTO toRow(ForumPost post, long commentCount, ForumCommunity community) {
        AdminForumPostRowDTO row = new AdminForumPostRowDTO();
        row.setId(post.getId());
        row.setTitle(post.getTitle());
        row.setExcerpt(post.getExcerpt());
        row.setUserId(post.getUserId());
        row.setCommunityId(post.getCommunityId());
        row.setViews(post.getViews() != null ? post.getViews() : 0);
        row.setCommentCount((int) commentCount);
        row.setIsPinned(post.getIsPinned() != null ? post.getIsPinned() : false);
        row.setIsLocked(post.getIsLocked() != null ? post.getIsLocked() : false);
        row.setIsFlagged(post.getIsFlagged() != null ? post.getIsFlagged() : false);
        row.setFlaggedReason(post.getFlaggedReason());
        row.setFlaggedAt(post.getFlaggedAt());
        row.setIsDeleted(post.getIsDeleted() != null ? post.getIsDeleted() : false);
        row.setDeletedAt(post.getDeletedAt());
        row.setCreatedAt(post.getCreatedAt());
        row.setUpdatedAt(post.getCreatedAt());
        if (community != null) {
            row.setCommunityName(community.getName());
            row.setCommunitySlug(community.getSlug());
        }
        return row;
    }
}
