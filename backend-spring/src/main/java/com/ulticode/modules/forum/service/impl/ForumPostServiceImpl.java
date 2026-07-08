package com.ulticode.modules.forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.annotation.CheckBan;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.forum.dto.*;
import com.ulticode.modules.forum.entity.ForumCommunity;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.entity.ForumUser;
import com.ulticode.modules.forum.mapper.ForumCommunityMapper;
import com.ulticode.modules.forum.mapper.ForumCommunityMemberMapper;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import com.ulticode.modules.forum.mapper.ForumUserMapper;
import com.ulticode.modules.forum.projection.ForumReadProjection;
import com.ulticode.modules.forum.service.ForumPostService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Write-side service for forum posts. Owns the transactional create / update /
 * delete paths and the SQL + paging for the read paths.
 *
 * <p><b>Deepened.</b> All entity-to-VO projection rules and the batch-load
 * helpers live behind {@link ForumReadProjection}; this service delegates to
 * it for any VO it returns (including the VO returned from the write paths).
 * The service no longer needs to know how a {@code ForumPostVO} is built —
 * the projection owns those rules.
 *
 * <p>Reads that return VOs ({@link #findAllPosts}, {@link #findMyPosts},
 * {@link #findPostById}, {@link #getPostThread}) still cross this seam
 * because the SQL + paging live here; the projection is invoked for VO
 * assembly. This is the same shape as {@code ModerationProjection} /
 * {@code ForumPostService} across the inversion series.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForumPostServiceImpl implements ForumPostService {

    private static final int MAX_RECENT_POSTS = 50;

    private final ForumPostMapper postMapper;
    private final ForumCommunityMapper communityMapper;
    private final ForumCommunityMemberMapper memberMapper;
    private final ForumUserMapper forumUserMapper;
    private final UserService userService;
    /**
     * Projection for entity-to-VO assembly. Injected so the write paths
     * (createPost, updatePost, getPostThread) can return a VO without the
     * service re-implementing the projection rules. This is the seam fix —
     * the projection rules live in one module.
     */
    private final ForumReadProjection forumReadProjection;
    private final Clock clock;
    private final UuidGenerator uuidGenerator;

    // =========================================================================
    // Find all posts — uses selectPage + QueryWrapper for correct TypeHandler
    // =========================================================================

    @Override
    public List<ForumPostVO> findAllPosts(String userId) {
        return findAllPosts(userId, "new", 1, MAX_RECENT_POSTS).getItems();
    }

    @Override
    public PageResult<ForumPostVO> findAllPosts(String userId, int page, int pageSize) {
        return findAllPosts(userId, "new", page, pageSize);
    }

    @Override
    public PageResult<ForumPostVO> findAllPosts(String userId, String sortBy, int page, int pageSize) {
        int limit = Math.max(1, Math.min(pageSize, MAX_RECENT_POSTS)),
            offset = Math.max(0, (page - 1) * limit);
        LambdaQueryWrapper<ForumPost> wrapper = new LambdaQueryWrapper<ForumPost>()
                .eq(ForumPost::getIsDeleted, false);
        applySortBy(wrapper, sortBy);
        long total = postMapper.selectCount(new LambdaQueryWrapper<ForumPost>().eq(ForumPost::getIsDeleted, false));
        List<ForumPost> posts = postMapper.selectList(wrapper.last("LIMIT " + limit + " OFFSET " + offset));
        Map<String, User> authorMap = forumReadProjection.batchLoadAuthors(posts);
        Map<String, ForumCommunity> communityMap = batchLoadCommunities(posts);
        Map<String, Long> commentCounts = forumReadProjection.batchLoadCommentCounts(posts);
        List<ForumPostVO> items = posts.stream()
                .map(p -> forumReadProjection.convertToPostVO(
                        p,
                        userId,
                        authorMap.get(p.getUserId()),
                        communityMap.get(p.getCommunityId()),
                        commentCounts.getOrDefault(p.getId(), 0L)))
                .collect(Collectors.toList());
        return PageResult.of(items, total, page, limit);
    }

    @Override
    public ForumPostVO findPostById(String id, String userId) {
        ForumPost post = postMapper.selectById(id);
        if (post == null) throw new BusinessException(ErrorCode.FORUM_POST_NOT_FOUND);
        User author = userService.findById(post.getUserId()).orElse(null);
        return forumReadProjection.convertToPostVO(post, userId, author);
    }

    // =========================================================================
    // My posts
    // =========================================================================

    @Override
    public List<ForumPostVO> findMyPosts(String userId) {
        return findMyPosts(userId, 1, MAX_RECENT_POSTS).getItems();
    }

    @Override
    public PageResult<ForumPostVO> findMyPosts(String userId, int page, int pageSize) {
        int limit = Math.max(1, Math.min(pageSize, MAX_RECENT_POSTS)),
            offset = Math.max(0, (page - 1) * limit);
        long total = postMapper.countByUserId(userId);
        List<ForumPost> posts = postMapper.findByUserId(userId);
        // Manual pagination since findByUserId returns full list
        List<ForumPost> paged = posts.stream().skip(offset).limit(limit).collect(Collectors.toList());
        Map<String, User> authorMap = forumReadProjection.batchLoadAuthors(paged);
        Map<String, ForumCommunity> communityMap = batchLoadCommunities(paged);
        Map<String, Long> commentCounts = forumReadProjection.batchLoadCommentCounts(paged);
        List<ForumPostVO> items = paged.stream()
                .map(p -> forumReadProjection.convertToPostVO(
                        p,
                        userId,
                        authorMap.get(p.getUserId()),
                        communityMap.get(p.getCommunityId()),
                        commentCounts.getOrDefault(p.getId(), 0L)))
                .collect(Collectors.toList());
        return PageResult.of(items, total, page, limit);
    }

    // =========================================================================
    // Create / Update / Delete
    // =========================================================================

    @Override
    @Transactional
    @CheckBan
    public ForumPostVO createPost(CreatePostDTO dto, String userId) {
        ForumCommunity community = communityMapper.selectById(dto.getCommunityId());
        if (community == null) throw new BusinessException(ErrorCode.FORUM_COMMUNITY_NOT_FOUND);
        if ("PRIVATE".equals(community.getVisibility()) && !memberMapper.isMember(dto.getCommunityId(), userId))
            throw new BusinessException(ErrorCode.FORUM_COMMUNITY_RESTRICTED);
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
        communityMapper.incrementPostsCount(dto.getCommunityId());
        User author = userService.findById(post.getUserId()).orElse(null);
        return forumReadProjection.convertToPostVO(post, userId, author);
    }

    @Override
    @Transactional
    public ForumPostVO updatePost(String id, UpdatePostDTO dto, String userId) {
        ForumPost post = postMapper.selectById(id);
        if (post == null) throw new BusinessException(ErrorCode.FORUM_POST_NOT_FOUND);
        if (!post.getUserId().equals(userId)) throw new BusinessException(ErrorCode.FORUM_CANNOT_EDIT_POST);
        if (Boolean.TRUE.equals(post.getIsLocked())) throw new BusinessException(ErrorCode.FORUM_POST_LOCKED);
        if (dto.getTitle() != null) post.setTitle(dto.getTitle());
        if (dto.getExcerpt() != null) post.setExcerpt(dto.getExcerpt());
        if (dto.getTags() != null) post.setTags(dto.getTags());
        if (dto.getFlairType() != null) post.setFlairType(dto.getFlairType());
        if (dto.getFlairLabel() != null) post.setFlairLabel(dto.getFlairLabel());
        if (dto.getMedia() != null) post.setMedia(dto.getMedia());
        if (dto.getIsPinned() != null) post.setIsPinned(dto.getIsPinned());
        if (dto.getIsLocked() != null) post.setIsLocked(dto.getIsLocked());
        postMapper.updateById(post);
        User author = userService.findById(post.getUserId()).orElse(null);
        ForumCommunity community = post.getCommunityId() != null ? communityMapper.selectById(post.getCommunityId()) : null;
        return forumReadProjection.convertToPostVO(post, userId, author, community, 0L);
    }

    @Override
    @Transactional
    public void deletePost(String id, String userId) {
        ForumPost post = postMapper.selectById(id);
        if (post == null) throw new BusinessException(ErrorCode.FORUM_POST_NOT_FOUND);
        if (!post.getUserId().equals(userId)) throw new BusinessException(ErrorCode.FORUM_CANNOT_DELETE_POST);
        postMapper.softDelete(id, userId);
        communityMapper.decrementPostsCount(post.getCommunityId());
    }

    // =========================================================================
    // Thread
    // =========================================================================

    @Override
    public ForumPostThreadVO getPostThread(String postId, String userId) {
        ForumPost post = postMapper.selectById(postId);
        if (post == null) throw new BusinessException(ErrorCode.FORUM_POST_NOT_FOUND);
        Map<String, User> authorMap = new HashMap<>();
        userService.findById(post.getUserId()).ifPresent(u -> authorMap.put(post.getUserId(), u));
        ForumCommunity community = post.getCommunityId() != null ? communityMapper.selectById(post.getCommunityId()) : null;
        ForumPostThreadVO thread = new ForumPostThreadVO();
        thread.setPost(forumReadProjection.convertToPostVO(post, userId,
                authorMap.get(post.getUserId()), community, 0L));
        // NOTE: comment tree building is done by the projection's
        // getPostThread() path which calls forumCommentService.buildCommentTree().
        // This base implementation leaves comments null; the projection sets them.
        return thread;
    }

    @Override
    @Transactional
    public void recordShare(String postId) {
        postMapper.incrementImpressions(postId);
    }

    @Override
    @Transactional
    public void recordView(String postId) {
        postMapper.incrementViews(postId);
    }

    @Override
    public long countByCommunityId(String cid) {
        return postMapper.countByCommunityId(cid);
    }

    @Override
    public List<ForumPost> findByCommunityId(String cid, int limit, int offset) {
        // Used by the projection for community listing — retained for compatibility.
        LambdaQueryWrapper<ForumPost> wrapper = new LambdaQueryWrapper<ForumPost>()
                .eq(ForumPost::getCommunityId, cid)
                .eq(ForumPost::getIsDeleted, false)
                .orderByDesc(ForumPost::getCreatedAt)
                .last("LIMIT " + limit + " OFFSET " + offset);
        return postMapper.selectList(wrapper);
    }

    // =========================================================================
    // SortBy logic
    // =========================================================================

    private void applySortBy(LambdaQueryWrapper<ForumPost> wrapper, String sortBy) {
        if (sortBy == null || sortBy.isEmpty() || "new".equals(sortBy)) {
            wrapper.orderByDesc(ForumPost::getCreatedAt);
        } else if ("hot".equals(sortBy)) {
            wrapper.orderByDesc(ForumPost::getViews);
        } else if ("top".equals(sortBy)) {
            wrapper.orderByDesc(ForumPost::getViews);
        } else {
            wrapper.orderByDesc(ForumPost::getCreatedAt);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Map<String, ForumCommunity> batchLoadCommunities(List<ForumPost> posts) {
        Set<String> ids = posts.stream().map(ForumPost::getCommunityId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Collections.emptyMap();
        return ids.stream()
                .map(communityMapper::selectById)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(ForumCommunity::getId, fc -> fc));
    }

    private String ensureForumUserExists(String userId) {
        ForumUser fu = forumUserMapper.selectById(userId);
        if (fu != null) return fu.getId();
        User user = userService.findById(userId).orElseThrow(() -> {
            log.error("User not found when creating forum user: {}", userId);
            return new BusinessException(ErrorCode.USER_NOT_FOUND);
        });
        ForumUser nu = new ForumUser();
        nu.setId(userId);
        nu.setUsername(user.getUsername());
        nu.setAvatar(user.getAvatar());
        nu.setKarma(0);
        nu.setCreatedAt(LocalDateTime.now(clock));
        forumUserMapper.insert(nu);
        log.debug("Created forum user entry for user: {} with id: {}", user.getUsername(), userId);
        return nu.getId();
    }

    private String generatePermalink() {
        return uuidGenerator.newId().replace("-", "").substring(0, 12);
    }
}