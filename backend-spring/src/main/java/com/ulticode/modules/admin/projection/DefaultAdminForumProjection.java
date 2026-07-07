package com.ulticode.modules.admin.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.PaginationRequest;
import com.ulticode.modules.admin.dto.AdminForumCommunityVO;
import com.ulticode.modules.admin.dto.AdminForumPostQueryDTO;
import com.ulticode.modules.admin.dto.AdminForumPostVO;
import com.ulticode.modules.forum.entity.ForumCommunity;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumCommentMapper;
import com.ulticode.modules.forum.mapper.ForumCommunityMapper;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import com.ulticode.modules.vote.entity.enums.EdgeOperationType;
import com.ulticode.modules.vote.mapper.EdgeOperationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default (and only) adapter for {@link AdminForumProjection}. Owns every
 * entity-to-VO projection rule and read-side aggregation for the admin forum
 * surface &mdash; see the interface javadoc for why this is a deep module.
 *
 * <p>All methods are pure reads; none mutate post or community state.
 * Batch-loads cross-module enrichment (user + community + comment count +
 * upvote / downvote counts) to keep the paginated list read N+1-safe.
 *
 * <p>Cross-module entity imports ({@link User}, {@link ForumCommunity},
 * {@link ForumCommentMapper}, {@link EdgeOperationMapper}) live here and only
 * here &mdash; the admin forum service no longer imports them after the
 * ADR-0011 Stage 2 extraction.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAdminForumProjection implements AdminForumProjection {

    private final ForumPostMapper forumPostMapper;
    private final ForumCommentMapper forumCommentMapper;
    private final ForumCommunityMapper forumCommunityMapper;
    private final UserMapper userMapper;
    private final EdgeOperationMapper edgeOperationMapper;

    // ------------------------------------------------------------------
    // Paginated post list read (query build + batch enrichment)
    // ------------------------------------------------------------------

    @Override
    public PageResult<AdminForumPostVO> getPosts(AdminForumPostQueryDTO query) {
        PaginationRequest pageRequest = PaginationRequest.of(query.getPage(), query.getLimit(), 10);

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
            case "commentCount" -> wrapper.orderBy(true, isAsc, ForumPost::getCreatedAt);
            default -> wrapper.orderBy(true, isAsc, ForumPost::getCreatedAt);
        }

        Page<ForumPost> pageResult = new Page<>(pageRequest.page(), pageRequest.pageSize());
        Page<ForumPost> result = forumPostMapper.selectPage(pageResult, wrapper);

        // Batch-load related data to avoid N+1 queries
        List<String> postIds = result.getRecords().stream()
                .map(ForumPost::getId)
                .toList();
        Set<String> userIds = result.getRecords().stream()
                .map(ForumPost::getUserId)
                .collect(Collectors.toSet());
        Set<String> communityIds = result.getRecords().stream()
                .map(ForumPost::getCommunityId)
                .collect(Collectors.toSet());

        Map<String, Long> commentCountMap = new HashMap<>();
        Map<String, Integer> upvoteMap = new HashMap<>();
        Map<String, Integer> downvoteMap = new HashMap<>();
        if (!postIds.isEmpty()) {
            forumCommentMapper.countByPostIds(postIds).forEach(row ->
                    commentCountMap.put((String) row.get("post_id"), ((Number) row.get("cnt")).longValue()));
            edgeOperationMapper.countByTargetsAndOperation(postIds,
                            EdgeOperationTargetType.FORUM_POST.name(), EdgeOperationType.VOTE_UP.name())
                    .forEach(row -> upvoteMap.put((String) row.get("target_id"), ((Number) row.get("cnt")).intValue()));
            edgeOperationMapper.countByTargetsAndOperation(postIds,
                            EdgeOperationTargetType.FORUM_POST.name(), EdgeOperationType.VOTE_DOWN.name())
                    .forEach(row -> downvoteMap.put((String) row.get("target_id"), ((Number) row.get("cnt")).intValue()));
        }

        Map<String, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userMap = userMapper.selectBatchIds(userIds).stream()
                    .collect(Collectors.toMap(User::getId, u -> u));
        }

        Map<String, ForumCommunity> communityMap = new HashMap<>();
        if (!communityIds.isEmpty()) {
            communityMap = forumCommunityMapper.selectBatchIds(communityIds).stream()
                    .collect(Collectors.toMap(ForumCommunity::getId, c -> c));
        }

        // Enrich with batch-loaded data
        Map<String, User> finalUserMap = userMap;
        Map<String, ForumCommunity> finalCommunityMap = communityMap;
        List<AdminForumPostVO> vos = result.getRecords().stream()
                .map(p -> toAdminVO(p, commentCountMap, upvoteMap, downvoteMap, finalUserMap, finalCommunityMap))
                .collect(Collectors.toList());

        // Sort by commentCount if requested (count derived post-fetch)
        if ("commentCount".equals(sortBy)) {
            final boolean asc = isAsc;
            vos.sort((a, b) -> {
                int countA = a.getCommentCount() != null ? a.getCommentCount() : 0;
                int countB = b.getCommentCount() != null ? b.getCommentCount() : 0;
                return asc ? Integer.compare(countA, countB) : Integer.compare(countB, countA);
            });
        }

        return PageResult.of(
                vos,
                result.getTotal(),
                pageRequest
        );
    }

    // ------------------------------------------------------------------
    // Single-item detail read
    // ------------------------------------------------------------------

    @Override
    public AdminForumPostVO getPost(String id) {
        ForumPost post = forumPostMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return toAdminVOWithDetails(post);
    }

    // ------------------------------------------------------------------
    // Community list read (filter dropdown source)
    // ------------------------------------------------------------------

    @Override
    public PageResult<AdminForumCommunityVO> getCommunities(int page, int limit, String search) {
        PaginationRequest communitiesRequest = PaginationRequest.of(page, limit);

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

        Page<ForumCommunity> pageResult = new Page<>(communitiesRequest.page(), communitiesRequest.pageSize());
        Page<ForumCommunity> result = forumCommunityMapper.selectPage(pageResult, wrapper);

        List<AdminForumCommunityVO> voList = result.getRecords().stream()
                .map(this::toCommunityVO)
                .collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), communitiesRequest);
    }

    // ------------------------------------------------------------------
    // Projection helpers (entity &rarr; AdminForumPostVO)
    // ------------------------------------------------------------------

    /**
     * Convert a ForumPost entity to a list-view AdminForumPostVO using
     * pre-loaded batch maps (avoids N+1 on the paginated read path).
     */
    private AdminForumPostVO toAdminVO(ForumPost post, Map<String, Long> commentCountMap,
                                        Map<String, Integer> upvoteMap, Map<String, Integer> downvoteMap,
                                        Map<String, User> userMap, Map<String, ForumCommunity> communityMap) {
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
        vo.setCommentCount(commentCountMap.getOrDefault(post.getId(), 0L).intValue());
        vo.setUpvotes(upvoteMap.getOrDefault(post.getId(), 0));
        vo.setDownvotes(downvoteMap.getOrDefault(post.getId(), 0));
        vo.setIsPinned(post.getIsPinned() != null ? post.getIsPinned() : false);
        vo.setIsLocked(post.getIsLocked() != null ? post.getIsLocked() : false);
        vo.setIsFlagged(post.getIsFlagged() != null ? post.getIsFlagged() : false);
        vo.setFlaggedReason(post.getFlaggedReason());
        vo.setFlaggedAt(post.getFlaggedAt());
        vo.setIsDeleted(post.getIsDeleted() != null ? post.getIsDeleted() : false);
        vo.setDeletedAt(post.getDeletedAt());
        vo.setCreatedAt(post.getCreatedAt());
        vo.setUpdatedAt(post.getCreatedAt());

        User user = userMap.get(post.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setAvatar(user.getAvatar());
        }

        ForumCommunity community = communityMap.get(post.getCommunityId());
        if (community != null) {
            vo.setCommunityName(community.getName());
            vo.setCommunitySlug(community.getSlug());
        }

        return vo;
    }

    /**
     * Convert a ForumPost entity to a detail-view AdminForumPostVO (single
     * fetch path — enriches user + community + counts inline since the
     * volume is 1).
     */
    private AdminForumPostVO toAdminVOWithDetails(ForumPost post) {
        if (post == null) {
            return null;
        }

        AdminForumPostVO vo = new AdminForumPostVO();
        vo.setId(post.getId());
        vo.setTitle(post.getTitle());
        vo.setExcerpt(post.getExcerpt());
        vo.setContent(post.getExcerpt());
        vo.setUserId(post.getUserId());
        vo.setCommunityId(post.getCommunityId());
        vo.setViewCount(post.getViews() != null ? post.getViews() : 0);
        vo.setCommentCount((int) forumCommentMapper.countByPostId(post.getId()));
        vo.setUpvotes(edgeOperationMapper.countByTargetAndOperation(
                post.getId(), EdgeOperationTargetType.FORUM_POST.name(), EdgeOperationType.VOTE_UP.name()));
        vo.setDownvotes(edgeOperationMapper.countByTargetAndOperation(
                post.getId(), EdgeOperationTargetType.FORUM_POST.name(), EdgeOperationType.VOTE_DOWN.name()));
        vo.setIsPinned(post.getIsPinned() != null ? post.getIsPinned() : false);
        vo.setIsLocked(post.getIsLocked() != null ? post.getIsLocked() : false);
        vo.setIsFlagged(post.getIsFlagged() != null ? post.getIsFlagged() : false);
        vo.setFlaggedReason(post.getFlaggedReason());
        vo.setFlaggedAt(post.getFlaggedAt());
        vo.setIsDeleted(post.getIsDeleted() != null ? post.getIsDeleted() : false);
        vo.setDeletedAt(post.getDeletedAt());
        vo.setCreatedAt(post.getCreatedAt());
        vo.setUpdatedAt(post.getCreatedAt());

        User user = userMapper.selectById(post.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setAvatar(user.getAvatar());
        }

        ForumCommunity community = forumCommunityMapper.selectById(post.getCommunityId());
        if (community != null) {
            vo.setCommunityName(community.getName());
            vo.setCommunitySlug(community.getSlug());
        }

        return vo;
    }

    /**
     * Convert a ForumCommunity entity to an AdminForumCommunityVO for the
     * filter dropdown.
     */
    private AdminForumCommunityVO toCommunityVO(ForumCommunity community) {
        AdminForumCommunityVO vo = new AdminForumCommunityVO();
        vo.setId(community.getId());
        vo.setName(community.getName());
        vo.setSlug(community.getSlug());
        vo.setDescription(community.getDescription());
        vo.setPostCount(community.getPostsCount() != null ? community.getPostsCount() : 0);
        vo.setMemberCount(community.getMembers() != null ? community.getMembers() : 0);
        return vo;
    }
}
