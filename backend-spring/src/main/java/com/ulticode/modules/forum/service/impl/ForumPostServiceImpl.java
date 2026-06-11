package com.ulticode.modules.forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.annotation.CheckBan;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.forum.dto.*;
import com.ulticode.modules.forum.entity.*;
import com.ulticode.modules.forum.mapper.*;
import com.ulticode.modules.forum.service.ForumPostService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.service.UserService;
import com.ulticode.modules.vote.dto.VoteResultVO;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import com.ulticode.modules.vote.service.VoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForumPostServiceImpl implements ForumPostService {

    private static final int MAX_RECENT_POSTS = 50;

    private final ForumPostMapper postMapper;
    private final ForumCommunityMapper communityMapper;
    private final ForumCommunityMemberMapper memberMapper;
    private final ForumUserMapper forumUserMapper;
    private final ForumCommentMapper commentMapper;
    private final UserService userService;
    private final VoteService voteService;

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
        Map<String, User> authorMap = batchLoadAuthors(posts);
        Map<String, ForumCommunity> communityMap = batchLoadCommunities(posts);
        Map<String, Long> commentCounts = batchLoadCommentCounts(posts);
        List<ForumPostVO> items = posts.stream()
                .map(p -> convertToPostVO(
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
        ForumCommunity community = post.getCommunityId() != null ? communityMapper.selectById(post.getCommunityId()) : null;
        return convertToPostVO(post, userId, author, community);
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
        Map<String, User> authorMap = batchLoadAuthors(paged);
        Map<String, ForumCommunity> communityMap = batchLoadCommunities(paged);
        Map<String, Long> commentCounts = batchLoadCommentCounts(paged);
        List<ForumPostVO> items = paged.stream()
                .map(p -> convertToPostVO(
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
        return convertToPostVO(post, userId, author, community);
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
        return convertToPostVO(post, userId, author, community);
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
        List<ForumComment> comments = commentMapper.findByPostId(postId);
        Set<String> authorIds = comments.stream().map(ForumComment::getAuthorId).collect(Collectors.toSet());
        authorIds.add(post.getUserId());
        Map<String, User> authorMap = new HashMap<>();
        authorIds.forEach(aid -> userService.findById(aid).ifPresent(u -> authorMap.put(aid, u)));
        ForumCommunity community = post.getCommunityId() != null ? communityMapper.selectById(post.getCommunityId()) : null;
        ForumPostThreadVO thread = new ForumPostThreadVO();
        thread.setPost(convertToPostVO(post, userId, authorMap.get(post.getUserId()), community));
        // NOTE: comment tree building is done by ForumServiceImpl.getPostThread() which
        // overrides this method and calls forumCommentService.buildCommentTree().
        // This base implementation leaves comments null — ForumServiceImpl sets them.
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
        // Used by ForumServiceImpl for community listing — retained for compatibility
        LambdaQueryWrapper<ForumPost> wrapper = new LambdaQueryWrapper<ForumPost>()
                .eq(ForumPost::getCommunityId, cid)
                .eq(ForumPost::getIsDeleted, false)
                .orderByDesc(ForumPost::getCreatedAt)
                .last("LIMIT " + limit + " OFFSET " + offset);
        return postMapper.selectList(wrapper);
    }

    // =========================================================================
    // Batch loading helpers
    // =========================================================================

    @Override
    public Map<String, User> batchLoadAuthors(List<ForumPost> posts) {
        Set<String> ids = posts.stream().map(ForumPost::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        return userService.findAllById(ids);
    }

    private Map<String, ForumCommunity> batchLoadCommunities(List<ForumPost> posts) {
        Set<String> ids = posts.stream().map(ForumPost::getCommunityId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Collections.emptyMap();
        return ids.stream()
                .map(communityMapper::selectById)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(ForumCommunity::getId, Function.identity()));
    }

    public Map<String, Long> batchLoadCommentCounts(List<ForumPost> posts) {
        List<String> ids = posts.stream()
                .map(ForumPost::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) return Collections.emptyMap();
        return commentMapper.countByPostIds(ids).stream()
                .collect(Collectors.toMap(
                        row -> String.valueOf(row.getOrDefault("post_id", row.get("postId"))),
                        row -> ((Number) row.getOrDefault("cnt", row.get("count"))).longValue()));
    }

    // =========================================================================
    // Entity → VO conversion (core fix: JSON fields + missing VO fields)
    // =========================================================================

    @Override
    public ForumPostVO convertToPostVO(ForumPost post, String userId, User author) {
        // Legacy signature — community not provided, look up individually
        ForumCommunity community = post.getCommunityId() != null ? communityMapper.selectById(post.getCommunityId()) : null;
        return convertToPostVO(post, userId, author, community);
    }

    public ForumPostVO convertToPostVO(ForumPost post, String userId, User author, ForumCommunity community) {
        long realCommentCount = post.getId() != null ? commentMapper.countByPostId(post.getId()) : 0L;
        return convertToPostVO(post, userId, author, community, realCommentCount);
    }

    public ForumPostVO convertToPostVO(
            ForumPost post,
            String userId,
            User author,
            ForumCommunity community,
            long realCommentCount) {
        ForumPostVO vo = new ForumPostVO();
        vo.setId(post.getId());
        vo.setCommunityId(post.getCommunityId());
        vo.setUserId(post.getUserId());
        vo.setPermalink(post.getPermalink());
        vo.setTitle(post.getTitle());
        vo.setFlairType(post.getFlairType());
        vo.setFlairLabel(post.getFlairLabel());
        vo.setExcerpt(post.getExcerpt());
        vo.setIsSaved(post.getIsSaved());
        vo.setImpressions(post.getImpressions());
        vo.setIsPinned(post.getIsPinned());
        vo.setIsLocked(post.getIsLocked());
        vo.setViews(post.getViews());
        vo.setIsFlagged(post.getIsFlagged());
        vo.setFlaggedReason(post.getFlaggedReason());
        vo.setFlaggedAt(post.getFlaggedAt());
        vo.setCreatedAt(post.getCreatedAt());
        vo.setIsAuthor(userId != null && post.getUserId() != null && post.getUserId().equals(userId));

        // --- Tags: ensure always List<String> ---
        if (post.getTags() instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> tagList = (List<String>) post.getTags();
            vo.setTags(tagList);
        } else if (post.getTags() instanceof String) {
            // Fallback: parse JSON string (shouldn't happen with selectById/selectList)
            try {
                @SuppressWarnings("unchecked")
                List<String> parsed = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                        (String) post.getTags(), List.class);
                vo.setTags(parsed);
            } catch (Exception e) {
                log.warn("Failed to parse tags JSON for post {}: {}", post.getId(), e.getMessage());
                vo.setTags(Collections.emptyList());
            }
        } else {
            vo.setTags(Collections.emptyList());
        }

        // --- Media: ensure parsed object, not raw JSON string ---
        Object media = post.getMedia();
        if (media instanceof String) {
            try {
                media = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                        (String) media, Object.class);
            } catch (Exception e) {
                log.warn("Failed to parse media JSON for post {}: {}", post.getId(), e.getMessage());
                media = null;
            }
        }
        vo.setMedia(media);

        // --- Stats: ensure always Map with likes/dislikes injected ---
        VoteResultVO vr = voteService.getVoteStatus(userId, post.getId(), EdgeOperationTargetType.FORUM_POST);
        vo.setVoteState(vr.getUserVote() == 1 ? "upvoted" : vr.getUserVote() == -1 ? "downvoted" : "neutral");

        LinkedHashMap<String, Object> statsMap = new LinkedHashMap<>();
        Object rawStats = post.getStats();
        if (rawStats instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> existing = (Map<String, Object>) rawStats;
            statsMap.putAll(existing);
        } else if (rawStats instanceof String) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                        (String) rawStats, LinkedHashMap.class);
                statsMap.putAll(parsed);
            } catch (Exception e) {
                log.warn("Failed to parse stats JSON for post {}: {}", post.getId(), e.getMessage());
            }
        }
        statsMap.put("likes", vr.getLikes());
        statsMap.put("dislikes", vr.getDislikes());
        statsMap.put("score", vr.getLikes() - vr.getDislikes());
        statsMap.put("comments", realCommentCount);
        vo.setStats(statsMap);

        vo.setCommentCount(realCommentCount);

        // --- Community name/slug ---
        if (community != null) {
            vo.setCommunityName(community.getName());
            vo.setCommunitySlug(community.getSlug());
        }

        // --- Author ---
        if (author != null) {
            vo.setAuthorUsername(author.getUsername());
            vo.setAuthorAvatar(author.getAvatar());
        }

        // --- Membership ---
        if (userId != null) {
            vo.setIsMember(memberMapper.isMember(post.getCommunityId(), userId));
        }

        return vo;
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
        nu.setCreatedAt(LocalDateTime.now());
        forumUserMapper.insert(nu);
        log.debug("Created forum user entry for user: {} with id: {}", user.getUsername(), userId);
        return nu.getId();
    }

    private String generatePermalink() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    @Override
    public ForumCommunityVO toCommunityVO(ForumCommunity c) {
        ForumCommunityVO v = new ForumCommunityVO();
        v.setId(c.getId());
        v.setName(c.getName());
        v.setSlug(c.getSlug());
        v.setDescription(c.getDescription());
        v.setMembers(c.getMembers());
        v.setOnline(c.getOnline());
        v.setIcon(c.getIcon());
        v.setColor(c.getColor());
        v.setBanner(c.getBanner());
        v.setPostsCount(c.getPostsCount());
        v.setPostsToday(c.getPostsToday());
        v.setPostsWeek(c.getPostsWeek());
        v.setIsOfficial(c.getIsOfficial());
        v.setIsFeatured(c.getIsFeatured());
        v.setSortOrder(c.getSortOrder());
        v.setCreatedAt(c.getCreatedAt());
        v.setVisibility(c.getVisibility());
        return v;
    }

    @Override
    public ForumTagVO toTagVO(ForumTag t) {
        ForumTagVO v = new ForumTagVO();
        v.setId(t.getId());
        v.setName(t.getName());
        v.setSlug(t.getSlug());
        v.setDescription(t.getDescription());
        v.setColor(t.getColor());
        v.setUsageCount(t.getUsageCount());
        v.setCreatedAt(t.getCreatedAt());
        return v;
    }

}
