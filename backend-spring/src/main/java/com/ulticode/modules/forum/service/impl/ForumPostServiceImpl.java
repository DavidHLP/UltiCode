package com.ulticode.modules.forum.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.forum.dto.CreatePostDTO;
import com.ulticode.modules.forum.dto.ForumPostVO;
import com.ulticode.modules.forum.dto.UpdatePostDTO;
import com.ulticode.modules.forum.projection.ForumPostProjection;
import com.ulticode.modules.forum.entity.ForumCommunity;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.lifecycle.ForumUserLifecyclePort;
import com.ulticode.modules.forum.mapper.ForumCommentMapper;
import com.ulticode.modules.forum.mapper.ForumCommunityMapper;
import com.ulticode.modules.forum.mapper.ForumCommunityMemberMapper;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import com.ulticode.modules.forum.service.ForumPostService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.projection.UserReadProjection;
import com.ulticode.modules.vote.service.VoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write-side service for forum posts. Owns the transactional create / update /
 * delete paths plus share / view bumps.
 *
 * <p>Read-side code lives on {@link com.ulticode.modules.forum.projection.ForumReadProjection};
 * write paths that need to return a {@link ForumPostVO} build it via the injected
 * {@link ForumPostProjection} so the entity-to-VO shaping lives in exactly one
 * place and the write service does not duplicate it. (Spring Boot 3.x forbids
 * the previous constructor-injection cycle, which is why the projection is the
 * shared seam rather than a direct cross-module call.)
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForumPostServiceImpl implements ForumPostService {

    private final ForumPostMapper postMapper;
    private final ForumCommunityMapper communityMapper;
    private final ForumCommunityMemberMapper memberMapper;
    private final ForumCommentMapper commentMapper;
    private final ForumUserLifecyclePort forumUserLifecycle;
    private final UserReadProjection userReadProjection;
    private final VoteService voteService;
    private final UuidGenerator uuidGenerator;
    private final ForumPostProjection postProjection;

    // =========================================================================
    // Create / Update / Delete
    // =========================================================================

    @Override
    @Transactional
    @com.ulticode.common.annotation.CheckBan
    public ForumPostVO createPost(CreatePostDTO dto, String userId) {
        ForumCommunity community = communityMapper.selectById(dto.getCommunityId());
        if (community == null) throw new BusinessException(ErrorCode.FORUM_COMMUNITY_NOT_FOUND);
        if ("PRIVATE".equals(community.getVisibility()) && !memberMapper.isMember(dto.getCommunityId(), userId))
            throw new BusinessException(ErrorCode.FORUM_COMMUNITY_RESTRICTED);
        String forumUserId = forumUserLifecycle.resolveOrCreate(userId).getId();
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
        User author = userReadProjection.findById(post.getUserId()).orElse(null);
        return postProjection.toPostVO(post, userId, author);
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
        User author = userReadProjection.findById(post.getUserId()).orElse(null);
        ForumCommunity community = post.getCommunityId() != null ? communityMapper.selectById(post.getCommunityId()) : null;
        return postProjection.toPostVO(post, userId, author, community, 0L);
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
    // Share / view bumps
    // =========================================================================

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

    // =========================================================================
    // Helpers
    // =========================================================================

    private String generatePermalink() {
        return uuidGenerator.newId().replace("-", "").substring(0, 12);
    }
}