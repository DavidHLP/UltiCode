package com.ulticode.modules.forum.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.forum.dto.*;
import com.ulticode.modules.forum.entity.*;
import com.ulticode.modules.forum.mapper.*;
import com.ulticode.modules.forum.service.ForumCommentService;
import com.ulticode.modules.forum.service.ForumPostService;
import com.ulticode.modules.forum.service.ForumService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j @Service @RequiredArgsConstructor
public class ForumServiceImpl implements ForumService {

    private final ForumPostService forumPostService;
    private final ForumCommentService forumCommentService;
    private final ForumCommunityMapper communityMapper;
    private final ForumCommunityMemberMapper memberMapper;
    private final ForumTagMapper tagMapper;
    private final ForumCommentMapper commentMapper;
    private final ForumPostMapper postMapper;
    private final UserService userService;

    @Override public List<ForumPostVO> findAllPosts(String u) { return forumPostService.findAllPosts(u); }
    @Override public PageResult<ForumPostVO> findAllPosts(String u, int p, int ps) { return forumPostService.findAllPosts(u, p, ps); }
    @Override public ForumPostVO findPostById(String id, String u) { return forumPostService.findPostById(id, u); }
    @Override public List<ForumPostVO> findMyPosts(String u) { return forumPostService.findMyPosts(u); }
    @Override public PageResult<ForumPostVO> findMyPosts(String u, int p, int ps) { return forumPostService.findMyPosts(u, p, ps); }
    @Override public ForumPostVO createPost(CreatePostDTO d, String u) { return forumPostService.createPost(d, u); }
    @Override public ForumPostVO updatePost(String id, UpdatePostDTO d, String u) { return forumPostService.updatePost(id, d, u); }
    @Override public void deletePost(String id, String u) { forumPostService.deletePost(id, u); }
    @Override public void recordShare(String id) { forumPostService.recordShare(id); }
    @Override public void recordView(String id) { forumPostService.recordView(id); }
    @Override public ForumPostThreadVO getPostThread(String postId, String userId) {
        ForumPostThreadVO thread = forumPostService.getPostThread(postId, userId);
        List<ForumComment> cs = commentMapper.findByPostId(postId);
        Set<String> ids = new HashSet<>(); cs.forEach(c -> ids.add(c.getAuthorId()));
        ForumPost p = postMapper.selectById(postId); if (p != null && p.getUserId() != null) ids.add(p.getUserId());
        thread.setComments(forumCommentService.buildCommentTree(cs, userService.findAllById(ids))); return thread;
    }
    @Override public ForumCommentVO createComment(String pid, CreateCommentDTO d, String u) { return forumCommentService.createComment(pid, d, u); }
    @Override public ForumCommentVO updateComment(String id, UpdateCommentDTO d, String u) { return forumCommentService.updateComment(id, d, u); }
    @Override public void deleteComment(String id, String u) { forumCommentService.deleteComment(id, u); }
    @Override public List<ForumCommunityVO> findAllCommunities(boolean f) {
        return (f ? communityMapper.findFeaturedCommunities() : communityMapper.findPublicCommunities()).stream().map(forumPostService::toCommunityVO).collect(Collectors.toList());
    }
    @Override public ForumCommunityDetailVO findCommunityBySlugOrId(String s) {
        ForumCommunity c = communityMapper.findBySlug(s); if (c == null) c = communityMapper.selectById(s);
        if (c == null) throw new BusinessException(ErrorCode.FORUM_COMMUNITY_NOT_FOUND);
        ForumCommunityDetailVO d = new ForumCommunityDetailVO();
        d.setCommunity(forumPostService.toCommunityVO(c)); d.setRules(Collections.emptyList()); d.setLinks(Collections.emptyList()); return d;
    }
    @Override public List<ForumPostVO> findPostsByCommunity(String slug, String sortBy, String u) { return findPostsByCommunity(slug, sortBy, u, 1, 50).getItems(); }
    @Override public PageResult<ForumPostVO> findPostsByCommunity(String slug, String sortBy, String u, int page, int pageSize) {
        ForumCommunity c = communityMapper.findBySlug(slug); if (c == null) throw new BusinessException(ErrorCode.FORUM_COMMUNITY_NOT_FOUND);
        int limit = Math.min(pageSize, 50), offset = (page - 1) * limit;
        List<ForumPost> ps = forumPostService.findByCommunityId(c.getId(), limit, offset);
        Map<String, User> am = forumPostService.batchLoadAuthors(ps);
        return PageResult.of(ps.stream().map(p -> forumPostService.convertToPostVO(p, u, am.get(p.getUserId()))).collect(Collectors.toList()),
                forumPostService.countByCommunityId(c.getId()), page, limit);
    }
    @Override @Transactional public void joinCommunity(String cid, String uid) {
        if (communityMapper.selectById(cid) == null) throw new BusinessException(ErrorCode.FORUM_COMMUNITY_NOT_FOUND);
        if (!memberMapper.isMember(cid, uid)) { ForumCommunityMember m = new ForumCommunityMember();
            m.setCommunityId(cid); m.setUserId(uid); m.setRole("MEMBER"); m.setJoinedAt(java.time.LocalDateTime.now());
            memberMapper.insert(m); communityMapper.incrementMembers(cid); }
    }
    @Override @Transactional public void leaveCommunity(String cid, String uid) { memberMapper.deleteByCommunityIdAndUserId(cid, uid); communityMapper.decrementMembers(cid); }
    @Override public List<ForumTagVO> findAllTags() { return tagMapper.findAllOrderByUsage().stream().map(forumPostService::toTagVO).collect(Collectors.toList()); }
    @Override public List<QuickFilterDTO> getQuickFilters() { return List.of(new QuickFilterDTO("Hot", "hot"), new QuickFilterDTO("New", "new"), new QuickFilterDTO("Top", "top")); }
}
