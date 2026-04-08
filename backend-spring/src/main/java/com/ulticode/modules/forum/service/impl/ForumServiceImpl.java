package com.ulticode.modules.forum.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.forum.dto.*;
import com.ulticode.modules.forum.entity.*;
import com.ulticode.modules.forum.mapper.*;
import com.ulticode.modules.forum.service.ForumService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Forum Service Implementation.
 * Implements business logic for forum operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ForumServiceImpl implements ForumService {

    private static final int MAX_RECENT_POSTS = 50;

    private final ForumPostMapper postMapper;
    private final ForumCommentMapper commentMapper;
    private final ForumCommunityMapper communityMapper;
    private final ForumCommunityMemberMapper memberMapper;
    private final ForumTagMapper tagMapper;
    private final ForumUserMapper forumUserMapper;
    private final UserService userService;

    // =========================================================================
    // POST OPERATIONS
    // =========================================================================

    @Override
    public List<ForumPostVO> findAllPosts(String userId) {
        log.debug("Finding all posts for userId: {}", userId);
        List<ForumPost> posts = postMapper.findRecentPosts(MAX_RECENT_POSTS);
        Map<String, User> authorMap = batchLoadAuthors(posts);
        return posts.stream()
                .map(post -> convertToPostVO(post, userId, authorMap.get(post.getUserId())))
                .collect(Collectors.toList());
    }

    @Override
    public ForumPostVO findPostById(String id, String userId) {
        log.debug("Finding post by id: {} for userId: {}", id, userId);
        ForumPost post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(ErrorCode.FORUM_POST_NOT_FOUND);
        }
        User author = userService.findById(post.getUserId()).orElse(null);
        return convertToPostVO(post, userId, author);
    }

    @Override
    public List<ForumPostVO> findMyPosts(String userId) {
        log.debug("Finding posts for user: {}", userId);
        List<ForumPost> posts = postMapper.findByUserId(userId);
        Map<String, User> authorMap = batchLoadAuthors(posts);
        return posts.stream()
                .map(post -> convertToPostVO(post, userId, authorMap.get(post.getUserId())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ForumPostVO createPost(CreatePostDTO dto, String userId) {
        log.debug("Creating post for user: {}", userId);

        // Validate community exists
        ForumCommunity community = communityMapper.selectById(dto.getCommunityId());
        if (community == null) {
            throw new BusinessException(ErrorCode.FORUM_COMMUNITY_NOT_FOUND);
        }

        // Check if community is restricted (private)
        if ("PRIVATE".equals(community.getVisibility())) {
            // Check if user is a member
            if (!memberMapper.isMember(dto.getCommunityId(), userId)) {
                throw new BusinessException(ErrorCode.FORUM_COMMUNITY_RESTRICTED);
            }
        }

        // Ensure forum user exists (find or create) - forum_posts.user_id references forum_users.id
        String forumUserId = ensureForumUserExists(userId);

        ForumPost post = new ForumPost();
        post.setCommunityId(dto.getCommunityId());
        post.setUserId(forumUserId);
        post.setPermalink(generatePermalink());
        post.setTitle(dto.getTitle());
        post.setFlairType(dto.getFlairType());
        post.setFlairLabel(dto.getFlairLabel());
        post.setTags(dto.getTags());
        post.setExcerpt(dto.getExcerpt() != null ? dto.getExcerpt() : dto.getBody());
        post.setMedia(dto.getMedia());
        post.setVoteState("neutral");
        post.setIsSaved(false);
        post.setImpressions(0);
        post.setIsPinned(false);
        post.setIsLocked(false);
        post.setViews(0);
        post.setIsFlagged(false);

        postMapper.insert(post);

        // Increment community post count
        communityMapper.incrementPostsCount(dto.getCommunityId());

        User author = userService.findById(post.getUserId()).orElse(null);
        return convertToPostVO(post, userId, author);
    }

    @Override
    @Transactional
    public ForumPostVO updatePost(String id, UpdatePostDTO dto, String userId) {
        log.debug("Updating post: {} for user: {}", id, userId);

        ForumPost post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(ErrorCode.FORUM_POST_NOT_FOUND);
        }

        // Check ownership
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORUM_CANNOT_EDIT_POST);
        }

        // Check if post is locked
        if (Boolean.TRUE.equals(post.getIsLocked())) {
            throw new BusinessException(ErrorCode.FORUM_POST_LOCKED);
        }

        // Update fields
        if (dto.getTitle() != null) {
            post.setTitle(dto.getTitle());
        }
        if (dto.getExcerpt() != null) {
            post.setExcerpt(dto.getExcerpt());
        }
        if (dto.getTags() != null) {
            post.setTags(dto.getTags());
        }
        if (dto.getFlairType() != null) {
            post.setFlairType(dto.getFlairType());
        }
        if (dto.getFlairLabel() != null) {
            post.setFlairLabel(dto.getFlairLabel());
        }
        if (dto.getMedia() != null) {
            post.setMedia(dto.getMedia());
        }
        if (dto.getIsPinned() != null) {
            post.setIsPinned(dto.getIsPinned());
        }
        if (dto.getIsLocked() != null) {
            post.setIsLocked(dto.getIsLocked());
        }

        postMapper.updateById(post);

        User author = userService.findById(post.getUserId()).orElse(null);
        return convertToPostVO(post, userId, author);
    }

    @Override
    @Transactional
    public void deletePost(String id, String userId) {
        log.debug("Deleting post: {} for user: {}", id, userId);

        ForumPost post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(ErrorCode.FORUM_POST_NOT_FOUND);
        }

        // Check ownership
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORUM_CANNOT_DELETE_POST);
        }

        // Soft delete
        postMapper.softDelete(id, userId);

        // Decrement community post count
        communityMapper.decrementPostsCount(post.getCommunityId());
    }

    @Override
    public ForumPostThreadVO getPostThread(String postId, String userId) {
        log.debug("Getting thread for post: {} for userId: {}", postId, userId);

        ForumPost post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.FORUM_POST_NOT_FOUND);
        }

        // Get all comments for the post
        List<ForumComment> comments = commentMapper.findByPostId(postId);

        // Batch fetch all authors to avoid N+1 queries (including post author)
        Set<String> authorIds = comments.stream()
                .map(ForumComment::getAuthorId)
                .collect(Collectors.toSet());
        authorIds.add(post.getUserId()); // Include post author

        Map<String, User> authorMap = new HashMap<>();
        for (String authorId : authorIds) {
            userService.findById(authorId).ifPresent(user -> authorMap.put(authorId, user));
        }

        // Build comment tree with author info
        List<ForumCommentVO> commentVOs = buildCommentTree(comments, authorMap);

        ForumPostThreadVO thread = new ForumPostThreadVO();
        thread.setPost(convertToPostVO(post, userId, authorMap.get(post.getUserId())));
        thread.setComments(commentVOs);

        return thread;
    }

    @Override
    @Transactional
    public void recordShare(String postId) {
        log.debug("Recording share for post: {}", postId);
        // In a real implementation, this would track share metrics
        // For now, we just increment impressions as a simple implementation
        postMapper.incrementImpressions(postId);
    }

    @Override
    @Transactional
    public void recordView(String postId) {
        log.debug("Recording view for post: {}", postId);
        postMapper.incrementViews(postId);
    }

    // =========================================================================
    // COMMENT OPERATIONS
    // =========================================================================

    @Override
    @Transactional
    public ForumCommentVO createComment(String postId, CreateCommentDTO dto, String userId) {
        log.debug("Creating comment on post: {} for user: {}", postId, userId);

        // Check post exists
        ForumPost post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.FORUM_POST_NOT_FOUND);
        }

        // Check if post is locked
        if (Boolean.TRUE.equals(post.getIsLocked())) {
            throw new BusinessException(ErrorCode.FORUM_POST_LOCKED);
        }

        // Ensure forum user exists (find or create) - forum_users.id must exist for FK constraint
        String forumUserId = ensureForumUserExists(userId);

        ForumComment comment = new ForumComment();
        comment.setPostId(postId);
        comment.setParentId(dto.getParentId());
        comment.setAuthorId(forumUserId);
        comment.setBody(dto.getBody());
        comment.setMarkdown(dto.getBody());
        comment.setIsPinned(false);
        comment.setIsLocked(false);
        comment.setIsFlagged(false);

        commentMapper.insert(comment);

        // Fetch author info for the response
        Map<String, User> authorMap = new HashMap<>();
        userService.findById(userId).ifPresent(user -> authorMap.put(userId, user));

        return convertToCommentVO(comment, authorMap);
    }

    /**
     * Ensures a forum_users entry exists for the given user.
     * If not exists, creates one using the main user's information.
     *
     * @param userId the main users table ID (UUID)
     * @return the forum_users.id to use for forum operations
     */
    private String ensureForumUserExists(String userId) {
        // Check if forum user already exists
        ForumUser forumUser = forumUserMapper.selectById(userId);
        if (forumUser != null) {
            return forumUser.getId();
        }

        // Need to create forum user entry - fetch main user info
        User user = userService.findById(userId)
                .orElseThrow(() -> {
                    log.error("User not found when creating forum user: {}", userId);
                    return new BusinessException(ErrorCode.USER_NOT_FOUND);
                });

        // Create forum user with same ID as main user
        ForumUser newForumUser = new ForumUser();
        newForumUser.setId(userId);
        newForumUser.setUsername(user.getUsername());
        newForumUser.setAvatar(user.getAvatar());
        newForumUser.setKarma(0);
        newForumUser.setCreatedAt(LocalDateTime.now());

        forumUserMapper.insert(newForumUser);
        log.debug("Created forum user entry for user: {} with id: {}", user.getUsername(), userId);

        return newForumUser.getId();
    }

    @Override
    @Transactional
    public ForumCommentVO updateComment(String id, UpdateCommentDTO dto, String userId) {
        log.debug("Updating comment: {} for user: {}", id, userId);

        ForumComment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new BusinessException(ErrorCode.FORUM_COMMENT_NOT_FOUND);
        }

        // Check ownership
        if (!comment.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORUM_CANNOT_EDIT_POST);
        }

        comment.setBody(dto.getBody());
        comment.setMarkdown(dto.getBody());
        commentMapper.updateById(comment);
        commentMapper.markAsEdited(id);

        // Fetch author info for the response
        Map<String, User> authorMap = new HashMap<>();
        userService.findById(comment.getAuthorId()).ifPresent(user -> authorMap.put(comment.getAuthorId(), user));

        return convertToCommentVO(comment, authorMap);
    }

    @Override
    @Transactional
    public void deleteComment(String id, String userId) {
        log.debug("Deleting comment: {} for user: {}", id, userId);

        ForumComment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new BusinessException(ErrorCode.FORUM_COMMENT_NOT_FOUND);
        }

        // Check ownership
        if (!comment.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORUM_CANNOT_DELETE_POST);
        }

        // Soft delete
        commentMapper.softDelete(id, userId);
    }

    // =========================================================================
    // COMMUNITY OPERATIONS
    // =========================================================================

    @Override
    public List<ForumCommunityVO> findAllCommunities(boolean featuredOnly) {
        log.debug("Finding all communities, featuredOnly: {}", featuredOnly);
        List<ForumCommunity> communities;
        if (featuredOnly) {
            communities = communityMapper.findFeaturedCommunities();
        } else {
            communities = communityMapper.findPublicCommunities();
        }
        return communities.stream()
                .map(this::convertToCommunityVO)
                .collect(Collectors.toList());
    }

    @Override
    public ForumCommunityDetailVO findCommunityBySlugOrId(String slugOrId) {
        log.debug("Finding community by slug or id: {}", slugOrId);

        ForumCommunity community;
        // Try to find by slug first
        community = communityMapper.findBySlug(slugOrId);

        // If not found by slug, try by ID
        if (community == null) {
            community = communityMapper.selectById(slugOrId);
        }

        if (community == null) {
            throw new BusinessException(ErrorCode.FORUM_COMMUNITY_NOT_FOUND);
        }

        // Convert to detail VO
        // In a real implementation, this would include rules and links from related tables
        ForumCommunityDetailVO detailVO = new ForumCommunityDetailVO();
        detailVO.setCommunity(convertToCommunityVO(community));
        detailVO.setRules(Collections.emptyList());
        detailVO.setLinks(Collections.emptyList());

        return detailVO;
    }

    @Override
    public List<ForumPostVO> findPostsByCommunity(String slug, String sortBy, String userId) {
        log.debug("Finding posts by community: {} sortBy: {}", slug, sortBy);

        ForumCommunity community = communityMapper.findBySlug(slug);
        if (community == null) {
            throw new BusinessException(ErrorCode.FORUM_COMMUNITY_NOT_FOUND);
        }

        List<ForumPost> posts = postMapper.findByCommunityId(community.getId());

        // Apply sorting (simplified - in real implementation would use proper sorting algorithms)
        if ("top".equals(sortBy)) {
            // Sort by views/impressions (already sorted by created_at desc)
            // For now, use default order
        }
        // 'hot' and 'new' use default created_at desc order

        Map<String, User> authorMap = batchLoadAuthors(posts);
        return posts.stream()
                .map(post -> convertToPostVO(post, userId, authorMap.get(post.getUserId())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void joinCommunity(String communityId, String userId) {
        log.debug("User {} joining community: {}", userId, communityId);

        ForumCommunity community = communityMapper.selectById(communityId);
        if (community == null) {
            throw new BusinessException(ErrorCode.FORUM_COMMUNITY_NOT_FOUND);
        }

        // Check if already a member
        if (memberMapper.isMember(communityId, userId)) {
            log.debug("User {} is already a member of community {}", userId, communityId);
            return;
        }

        ForumCommunityMember member = new ForumCommunityMember();
        member.setCommunityId(communityId);
        member.setUserId(userId);
        member.setRole("MEMBER");
        member.setJoinedAt(LocalDateTime.now());

        memberMapper.insert(member);
        communityMapper.incrementMembers(communityId);
    }

    @Override
    @Transactional
    public void leaveCommunity(String communityId, String userId) {
        log.debug("User {} leaving community: {}", userId, communityId);

        memberMapper.deleteByCommunityIdAndUserId(communityId, userId);
        communityMapper.decrementMembers(communityId);
    }

    // =========================================================================
    // TAG OPERATIONS
    // =========================================================================

    @Override
    public List<ForumTagVO> findAllTags() {
        log.debug("Finding all tags");
        List<ForumTag> tags = tagMapper.findAllOrderByUsage();
        return tags.stream()
                .map(this::convertToTagVO)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // QUICK FILTER OPERATIONS
    // =========================================================================

    @Override
    public List<QuickFilterDTO> getQuickFilters() {
        log.debug("Getting quick filters");
        // Returns the available filter options for forum posts
        // The label will be translated on the frontend using i18n
        return List.of(
                new QuickFilterDTO("Hot", "hot"),
                new QuickFilterDTO("New", "new"),
                new QuickFilterDTO("Top", "top")
        );
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private Map<String, User> batchLoadAuthors(List<ForumPost> posts) {
        Set<String> authorIds = posts.stream()
                .map(ForumPost::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return userService.findAllById(authorIds);
    }

    private ForumPostVO convertToPostVO(ForumPost post, String userId, User author) {
        ForumPostVO vo = new ForumPostVO();
        vo.setId(post.getId());
        vo.setCommunityId(post.getCommunityId());
        vo.setUserId(post.getUserId());
        vo.setPermalink(post.getPermalink());
        vo.setTitle(post.getTitle());
        vo.setFlairType(post.getFlairType());
        vo.setFlairLabel(post.getFlairLabel());
        // Convert tags from Object to List<String>
        if (post.getTags() instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) post.getTags();
            vo.setTags(tags);
        } else {
            vo.setTags(Collections.emptyList());
        }
        vo.setExcerpt(post.getExcerpt());
        vo.setMedia(post.getMedia());
        vo.setVoteState(post.getVoteState());
        vo.setIsSaved(post.getIsSaved());
        vo.setImpressions(post.getImpressions());
        vo.setIsPinned(post.getIsPinned());
        vo.setIsLocked(post.getIsLocked());
        vo.setStats(post.getStats());
        vo.setViews(post.getViews());
        vo.setIsFlagged(post.getIsFlagged());
        vo.setFlaggedReason(post.getFlaggedReason());
        vo.setFlaggedAt(post.getFlaggedAt());
        vo.setCreatedAt(post.getCreatedAt());

        // Populate author info if available
        if (author != null) {
            vo.setAuthorUsername(author.getUsername());
            vo.setAuthorAvatar(author.getAvatar());
        }

        // Check if user is member of community (if userId provided)
        if (userId != null) {
            vo.setIsMember(memberMapper.isMember(post.getCommunityId(), userId));
        }

        return vo;
    }

    private ForumCommentVO convertToCommentVO(ForumComment comment, Map<String, User> authorMap) {
        ForumCommentVO vo = new ForumCommentVO();
        vo.setId(comment.getId());
        vo.setPostId(comment.getPostId());
        vo.setParentId(comment.getParentId());
        vo.setAuthorId(comment.getAuthorId());

        // Populate author info from author map
        User author = authorMap.get(comment.getAuthorId());
        if (author != null) {
            vo.setAuthorUsername(author.getUsername());
            vo.setAuthorAvatar(author.getAvatar());
        }

        vo.setBody(comment.getBody());
        vo.setMarkdown(comment.getMarkdown());
        vo.setCreatedAt(comment.getCreatedAt());
        vo.setEditedAt(comment.getEditedAt());
        vo.setIsPinned(comment.getIsPinned());
        vo.setIsLocked(comment.getIsLocked());
        vo.setIsFlagged(comment.getIsFlagged());
        vo.setFlaggedReason(comment.getFlaggedReason());
        vo.setFlaggedAt(comment.getFlaggedAt());
        return vo;
    }

    private ForumCommunityVO convertToCommunityVO(ForumCommunity community) {
        ForumCommunityVO vo = new ForumCommunityVO();
        vo.setId(community.getId());
        vo.setName(community.getName());
        vo.setSlug(community.getSlug());
        vo.setDescription(community.getDescription());
        vo.setMembers(community.getMembers());
        vo.setOnline(community.getOnline());
        vo.setIcon(community.getIcon());
        vo.setColor(community.getColor());
        vo.setBanner(community.getBanner());
        vo.setPostsCount(community.getPostsCount());
        vo.setPostsToday(community.getPostsToday());
        vo.setPostsWeek(community.getPostsWeek());
        vo.setIsOfficial(community.getIsOfficial());
        vo.setIsFeatured(community.getIsFeatured());
        vo.setSortOrder(community.getSortOrder());
        vo.setCreatedAt(community.getCreatedAt());
        vo.setVisibility(community.getVisibility());
        return vo;
    }

    private ForumTagVO convertToTagVO(ForumTag tag) {
        ForumTagVO vo = new ForumTagVO();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
        vo.setSlug(tag.getSlug());
        vo.setDescription(tag.getDescription());
        vo.setColor(tag.getColor());
        vo.setUsageCount(tag.getUsageCount());
        vo.setCreatedAt(tag.getCreatedAt());
        return vo;
    }

    private List<ForumCommentVO> buildCommentTree(List<ForumComment> comments, Map<String, User> authorMap) {
        // Separate top-level comments and replies
        List<ForumComment> topLevelComments = comments.stream()
                .filter(c -> c.getParentId() == null)
                .collect(Collectors.toList());

        return topLevelComments.stream()
                .map(c -> {
                    ForumCommentVO vo = convertToCommentVO(c, authorMap);
                    // Recursively build replies
                    List<ForumCommentVO> replies = findReplies(c.getId(), comments, authorMap);
                    if (!replies.isEmpty()) {
                        vo.setReplies(replies);
                    }
                    return vo;
                })
                .collect(Collectors.toList());
    }

    private List<ForumCommentVO> findReplies(String parentId, List<ForumComment> allComments, Map<String, User> authorMap) {
        return allComments.stream()
                .filter(c -> parentId.equals(c.getParentId()))
                .map(c -> {
                    ForumCommentVO vo = convertToCommentVO(c, authorMap);
                    vo.setReplies(findReplies(c.getId(), allComments, authorMap));
                    return vo;
                })
                .collect(Collectors.toList());
    }

    private String generatePermalink() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
