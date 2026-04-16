package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminForumPostQueryDTO;
import com.ulticode.modules.admin.dto.AdminForumPostVO;
import com.ulticode.modules.admin.dto.BulkActionResult;
import com.ulticode.modules.admin.controller.AdminForumController.AdminForumCommunityVO;
import com.ulticode.modules.admin.service.AdminForumService;
import com.ulticode.modules.forum.entity.ForumCommunity;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumCommunityMapper;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of AdminForumService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminForumServiceImpl implements AdminForumService {

    private final ForumPostMapper forumPostMapper;
    private final UserMapper userMapper;
    private final ForumCommunityMapper forumCommunityMapper;

    @Override
    public PageResult<AdminForumPostVO> getPosts(AdminForumPostQueryDTO query) {
        int page = query.getPage() != null && query.getPage() > 0 ? query.getPage() : 1;
        int limit = query.getLimit() != null && query.getLimit() > 0 ? Math.min(query.getLimit(), 100) : 10;

        LambdaQueryWrapper<ForumPost> wrapper = new LambdaQueryWrapper<>();

        // Search filter (title or excerpt)
        if (StringUtils.hasText(query.getSearch())) {
            wrapper.and(w -> w
                    .like(ForumPost::getTitle, "%" + query.getSearch() + "%")
                    .or()
                    .like(ForumPost::getExcerpt, "%" + query.getSearch() + "%"));
        }

        // Community ID filter
        if (StringUtils.hasText(query.getCommunityId())) {
            wrapper.eq(ForumPost::getCommunityId, query.getCommunityId());
        }

        // Author ID filter
        if (StringUtils.hasText(query.getAuthorId())) {
            wrapper.eq(ForumPost::getUserId, query.getAuthorId());
        }

        // Flagged status filter
        if (query.getIsFlagged() != null) {
            wrapper.eq(ForumPost::getIsFlagged, query.getIsFlagged());
        }

        // Pinned status filter
        if (query.getIsPinned() != null) {
            wrapper.eq(ForumPost::getIsPinned, query.getIsPinned());
        }

        // Locked status filter
        if (query.getIsLocked() != null) {
            wrapper.eq(ForumPost::getIsLocked, query.getIsLocked());
        }

        // Deleted status filter
        if (query.getIsDeleted() != null) {
            wrapper.eq(ForumPost::getIsDeleted, query.getIsDeleted());
        }

        // Sorting
        boolean isAsc = !"desc".equalsIgnoreCase(query.getSortOrder());
        String sortBy = StringUtils.hasText(query.getSortBy()) ? query.getSortBy() : "createdAt";
        switch (sortBy) {
            case "createdAt" -> wrapper.orderBy(true, isAsc, ForumPost::getCreatedAt);
            case "viewCount" -> wrapper.orderBy(true, isAsc, ForumPost::getViews);
            case "commentCount" -> {
                // For comment count, we'll need to order after fetching
                // Default to created date for now
                wrapper.orderBy(true, isAsc, ForumPost::getCreatedAt);
            }
            default -> wrapper.orderBy(true, isAsc, ForumPost::getCreatedAt);
        }

        Page<ForumPost> pageResult = new Page<>(page, limit);
        Page<ForumPost> result = forumPostMapper.selectPage(pageResult, wrapper);

        // Enrich with user and community information
        List<AdminForumPostVO> vos = result.getRecords().stream()
                .map(this::toAdminVO)
                .collect(Collectors.toList());

        return PageResult.of(
                vos,
                result.getTotal(),
                page,
                limit
        );
    }

    @Override
    public AdminForumPostVO getPost(String id) {
        ForumPost post = forumPostMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return toAdminVOWithDetails(post);
    }

    @Override
    public void pinPost(String id) {
        ForumPost post = getPostEntityOrThrow(id);
        post.setIsPinned(true);
        forumPostMapper.updateById(post);
        log.info("Post pinned: {}", id);
    }

    @Override
    public void unpinPost(String id) {
        ForumPost post = getPostEntityOrThrow(id);
        post.setIsPinned(false);
        forumPostMapper.updateById(post);
        log.info("Post unpinned: {}", id);
    }

    @Override
    public void lockPost(String id) {
        ForumPost post = getPostEntityOrThrow(id);
        post.setIsLocked(true);
        forumPostMapper.updateById(post);
        log.info("Post locked: {}", id);
    }

    @Override
    public void unlockPost(String id) {
        ForumPost post = getPostEntityOrThrow(id);
        post.setIsLocked(false);
        forumPostMapper.updateById(post);
        log.info("Post unlocked: {}", id);
    }

    @Override
    public void flagPost(String id, String reason) {
        ForumPost post = getPostEntityOrThrow(id);
        post.setIsFlagged(true);
        post.setFlaggedReason(reason);
        post.setFlaggedAt(LocalDateTime.now());
        forumPostMapper.updateById(post);
        log.info("Post flagged: {}, reason: {}", id, reason);
    }

    @Override
    public void unflagPost(String id) {
        ForumPost post = getPostEntityOrThrow(id);
        post.setIsFlagged(false);
        post.setFlaggedReason(null);
        post.setFlaggedAt(null);
        forumPostMapper.updateById(post);
        log.info("Post unflagged: {}", id);
    }

    @Override
    public void deletePost(String id) {
        ForumPost post = getPostEntityOrThrow(id);
        // Soft delete
        post.setIsDeleted(true);
        post.setDeletedAt(LocalDateTime.now());
        forumPostMapper.updateById(post);
        log.info("Post deleted: {}", id);
    }

    @Override
    public PageResult<AdminForumCommunityVO> getCommunities(int page, int limit) {
        int safeLimit = limit > 0 ? Math.min(limit, 100) : 20;
        int safePage = page > 0 ? page : 1;

        Page<ForumCommunity> pageResult = new Page<>(safePage, safeLimit);
        Page<ForumCommunity> result = forumCommunityMapper.selectPage(pageResult,
                new LambdaQueryWrapper<ForumCommunity>().orderByDesc(ForumCommunity::getMembers));

        List<AdminForumCommunityVO> voList = result.getRecords().stream()
                .map(c -> {
                    AdminForumCommunityVO vo = new AdminForumCommunityVO();
                    vo.setId(c.getId());
                    vo.setName(c.getName());
                    vo.setSlug(c.getSlug());
                    vo.setDescription(c.getDescription());
                    vo.setPostCount(c.getPostsCount() != null ? c.getPostsCount() : 0);
                    vo.setMemberCount(c.getMembers() != null ? c.getMembers() : 0);
                    return vo;
                })
                .collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), safePage, safeLimit);
    }

    @Override
    public BulkActionResult bulkAction(List<String> ids, String action) {
        BulkActionResult response = new BulkActionResult();
        response.setTotal(ids.size());
        response.setResults(new ArrayList<>());
        response.setSuccessful(0);
        response.setFailed(0);

        for (String id : ids) {
            BulkActionResult.BulkActionItem item = new BulkActionResult.BulkActionItem();
            item.setId(id);

            try {
                switch (action) {
                    case "delete" -> deletePost(id);
                    case "pin" -> pinPost(id);
                    case "unpin" -> unpinPost(id);
                    case "lock" -> lockPost(id);
                    case "unlock" -> unlockPost(id);
                    case "unflag" -> unflagPost(id);
                    default -> throw new IllegalArgumentException("Unknown action: " + action);
                }
                item.setSuccess(true);
                response.setSuccessful(response.getSuccessful() + 1);
            // broad catch: all failures map to same error response
            } catch (Exception e) {
                log.error("Failed to perform action {} on post {}", action, id, e);
                item.setSuccess(false);
                item.setError(e.getMessage());
                response.setFailed(response.getFailed() + 1);
            }

            response.getResults().add(item);
        }

        return response;
    }

    /**
     * Get ForumPost entity or throw exception.
     */
    private ForumPost getPostEntityOrThrow(String id) {
        ForumPost post = forumPostMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return post;
    }

    /**
     * Convert ForumPost entity to AdminForumPostVO (list view).
     */
    private AdminForumPostVO toAdminVO(ForumPost post) {
        if (post == null) {
            return null;
        }

        AdminForumPostVO vo = new AdminForumPostVO();
        vo.setId(post.getId());
        vo.setTitle(post.getTitle());
        vo.setExcerpt(post.getExcerpt());
        vo.setUserId(post.getUserId());
        vo.setCommunityId(post.getCommunityId());
        vo.setViewCount(post.getViews() != null ? post.getViews() : 0);
        vo.setCommentCount(0); // TODO: Query from forum_comments table
        vo.setUpvotes(0); // TODO: Query from forum_votes table
        vo.setDownvotes(0); // TODO: Query from forum_votes table
        vo.setIsPinned(post.getIsPinned() != null ? post.getIsPinned() : false);
        vo.setIsLocked(post.getIsLocked() != null ? post.getIsLocked() : false);
        vo.setIsFlagged(post.getIsFlagged() != null ? post.getIsFlagged() : false);
        vo.setFlaggedReason(post.getFlaggedReason());
        vo.setFlaggedAt(post.getFlaggedAt());
        vo.setIsDeleted(post.getIsDeleted() != null ? post.getIsDeleted() : false);
        vo.setDeletedAt(post.getDeletedAt());
        vo.setCreatedAt(post.getCreatedAt());
        vo.setUpdatedAt(post.getCreatedAt()); // No updatedAt field, use createdAt as fallback

        // Fetch user info
        User user = userMapper.selectById(post.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setAvatar(user.getAvatar());
        }

        // Fetch community info
        ForumCommunity community = forumCommunityMapper.selectById(post.getCommunityId());
        if (community != null) {
            vo.setCommunityName(community.getName());
            vo.setCommunitySlug(community.getSlug());
        }

        return vo;
    }

    /**
     * Convert ForumPost entity to AdminForumPostVO with full details.
     */
    private AdminForumPostVO toAdminVOWithDetails(ForumPost post) {
        AdminForumPostVO vo = toAdminVO(post);
        if (vo != null) {
            // Add full content for detail view
            // For now, we'll use excerpt as content since ForumPost doesn't have a separate content field
            vo.setContent(post.getExcerpt());
        }
        return vo;
    }
}
