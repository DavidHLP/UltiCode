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

    @Override
    public List<ForumPostVO> findAllPosts(String userId) {
        return findAllPosts(userId, 1, MAX_RECENT_POSTS).getItems();
    }

    @Override
    public PageResult<ForumPostVO> findAllPosts(String userId, int page, int pageSize) {
        int limit = Math.min(pageSize, MAX_RECENT_POSTS), offset = (page - 1) * limit;
        long total = postMapper.selectCount(new LambdaQueryWrapper<ForumPost>().eq(ForumPost::getIsDeleted, 0));
        List<ForumPost> posts = postMapper.findRecentPosts(limit, offset);
        Map<String, User> authorMap = batchLoadAuthors(posts);
        List<ForumPostVO> items = posts.stream().map(p -> convertToPostVO(p, userId, authorMap.get(p.getUserId()))).collect(Collectors.toList());
        return PageResult.of(items, total, page, limit);
    }

    @Override
    public ForumPostVO findPostById(String id, String userId) {
        ForumPost post = postMapper.selectById(id);
        if (post == null) throw new BusinessException(ErrorCode.FORUM_POST_NOT_FOUND);
        return convertToPostVO(post, userId, userService.findById(post.getUserId()).orElse(null));
    }

    @Override
    public List<ForumPostVO> findMyPosts(String userId) {
        return findMyPosts(userId, 1, MAX_RECENT_POSTS).getItems();
    }

    @Override
    public PageResult<ForumPostVO> findMyPosts(String userId, int page, int pageSize) {
        int limit = Math.min(pageSize, MAX_RECENT_POSTS), offset = (page - 1) * limit;
        long total = postMapper.countByUserId(userId);
        List<ForumPost> posts = postMapper.findByUserId(userId, limit, offset);
        Map<String, User> authorMap = batchLoadAuthors(posts);
        List<ForumPostVO> items = posts.stream().map(p -> convertToPostVO(p, userId, authorMap.get(p.getUserId()))).collect(Collectors.toList());
        return PageResult.of(items, total, page, limit);
    }

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
        post.setCommunityId(dto.getCommunityId()); post.setUserId(forumUserId); post.setPermalink(generatePermalink());
        post.setTitle(dto.getTitle()); post.setFlairType(dto.getFlairType()); post.setFlairLabel(dto.getFlairLabel());
        post.setTags(dto.getTags()); post.setExcerpt(dto.getExcerpt() != null ? dto.getExcerpt() : dto.getBody());
        post.setMedia(dto.getMedia()); post.setVoteState("neutral"); post.setIsSaved(false);
        post.setImpressions(0); post.setIsPinned(false); post.setIsLocked(false); post.setViews(0); post.setIsFlagged(false);
        postMapper.insert(post); communityMapper.incrementPostsCount(dto.getCommunityId());
        return convertToPostVO(post, userId, userService.findById(post.getUserId()).orElse(null));
    }

    @Override @Transactional
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
        return convertToPostVO(post, userId, userService.findById(post.getUserId()).orElse(null));
    }

    @Override @Transactional
    public void deletePost(String id, String userId) {
        ForumPost post = postMapper.selectById(id);
        if (post == null) throw new BusinessException(ErrorCode.FORUM_POST_NOT_FOUND);
        if (!post.getUserId().equals(userId)) throw new BusinessException(ErrorCode.FORUM_CANNOT_DELETE_POST);
        postMapper.softDelete(id, userId); communityMapper.decrementPostsCount(post.getCommunityId());
    }

    @Override
    public ForumPostThreadVO getPostThread(String postId, String userId) {
        ForumPost post = postMapper.selectById(postId);
        if (post == null) throw new BusinessException(ErrorCode.FORUM_POST_NOT_FOUND);
        List<ForumComment> comments = commentMapper.findByPostId(postId);
        Set<String> authorIds = comments.stream().map(ForumComment::getAuthorId).collect(Collectors.toSet());
        authorIds.add(post.getUserId());
        Map<String, User> authorMap = new HashMap<>();
        authorIds.forEach(aid -> userService.findById(aid).ifPresent(u -> authorMap.put(aid, u)));
        ForumPostThreadVO thread = new ForumPostThreadVO();
        thread.setPost(convertToPostVO(post, userId, authorMap.get(post.getUserId())));
        return thread;
    }

    @Override @Transactional
    public void recordShare(String postId) { postMapper.incrementImpressions(postId); }

    @Override @Transactional
    public void recordView(String postId) { postMapper.incrementViews(postId); }

    @Override public long countByCommunityId(String cid) { return postMapper.countByCommunityId(cid); }

    @Override public List<ForumPost> findByCommunityId(String cid, int limit, int offset) { return postMapper.findByCommunityId(cid, limit, offset); }

    public Map<String, User> batchLoadAuthors(List<ForumPost> posts) {
        Set<String> ids = posts.stream().map(ForumPost::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        return userService.findAllById(ids);
    }

    @Override
    public ForumPostVO convertToPostVO(ForumPost post, String userId, User author) {
        ForumPostVO vo = new ForumPostVO();
        vo.setId(post.getId()); vo.setCommunityId(post.getCommunityId()); vo.setUserId(post.getUserId());
        vo.setPermalink(post.getPermalink()); vo.setTitle(post.getTitle());
        vo.setFlairType(post.getFlairType()); vo.setFlairLabel(post.getFlairLabel());
        vo.setTags(post.getTags() instanceof List ? (List<String>) post.getTags() : Collections.emptyList());
        vo.setExcerpt(post.getExcerpt()); vo.setMedia(post.getMedia()); vo.setIsSaved(post.getIsSaved());
        vo.setImpressions(post.getImpressions()); vo.setIsPinned(post.getIsPinned()); vo.setIsLocked(post.getIsLocked());
        VoteResultVO vr = voteService.getVoteStatus(userId, post.getId(), EdgeOperationTargetType.FORUM_POST);
        vo.setVoteState(vr.getUserVote() == 1 ? "upvoted" : vr.getUserVote() == -1 ? "downvoted" : "neutral");
        if (post.getStats() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> es = new LinkedHashMap<>((Map<String, Object>) post.getStats());
            es.put("likes", vr.getLikes()); es.put("dislikes", vr.getDislikes()); vo.setStats(es);
        } else { vo.setStats(post.getStats()); }
        vo.setViews(post.getViews()); vo.setIsFlagged(post.getIsFlagged()); vo.setFlaggedReason(post.getFlaggedReason());
        vo.setFlaggedAt(post.getFlaggedAt()); vo.setCreatedAt(post.getCreatedAt());
        if (author != null) { vo.setAuthorUsername(author.getUsername()); vo.setAuthorAvatar(author.getAvatar()); }
        if (userId != null) { vo.setIsMember(memberMapper.isMember(post.getCommunityId(), userId)); }
        return vo;
    }

    private String ensureForumUserExists(String userId) {
        ForumUser fu = forumUserMapper.selectById(userId);
        if (fu != null) return fu.getId();
        User user = userService.findById(userId).orElseThrow(() -> {
            log.error("User not found when creating forum user: {}", userId);
            return new BusinessException(ErrorCode.USER_NOT_FOUND);
        });
        ForumUser nu = new ForumUser();
        nu.setId(userId); nu.setUsername(user.getUsername()); nu.setAvatar(user.getAvatar());
        nu.setKarma(0); nu.setCreatedAt(LocalDateTime.now());
        forumUserMapper.insert(nu);
        log.debug("Created forum user entry for user: {} with id: {}", user.getUsername(), userId);
        return nu.getId();
    }

    private String generatePermalink() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12); }

    @Override
    public ForumCommunityVO toCommunityVO(ForumCommunity c) {
        ForumCommunityVO v = new ForumCommunityVO();
        v.setId(c.getId()); v.setName(c.getName()); v.setSlug(c.getSlug()); v.setDescription(c.getDescription());
        v.setMembers(c.getMembers()); v.setOnline(c.getOnline()); v.setIcon(c.getIcon()); v.setColor(c.getColor());
        v.setBanner(c.getBanner()); v.setPostsCount(c.getPostsCount()); v.setPostsToday(c.getPostsToday());
        v.setPostsWeek(c.getPostsWeek()); v.setIsOfficial(c.getIsOfficial()); v.setIsFeatured(c.getIsFeatured());
        v.setSortOrder(c.getSortOrder()); v.setCreatedAt(c.getCreatedAt()); v.setVisibility(c.getVisibility());
        return v;
    }

    @Override
    public ForumTagVO toTagVO(ForumTag t) {
        ForumTagVO v = new ForumTagVO();
        v.setId(t.getId()); v.setName(t.getName()); v.setSlug(t.getSlug()); v.setDescription(t.getDescription());
        v.setColor(t.getColor()); v.setUsageCount(t.getUsageCount()); v.setCreatedAt(t.getCreatedAt());
        return v;
    }

}
