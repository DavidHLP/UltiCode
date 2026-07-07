package com.ulticode.modules.forum.port;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.forum.dto.CreateCommentDTO;
import com.ulticode.modules.forum.dto.CreatePostDTO;
import com.ulticode.modules.forum.dto.ForumCommentVO;
import com.ulticode.modules.forum.dto.ForumPostVO;
import com.ulticode.modules.forum.dto.UpdateCommentDTO;
import com.ulticode.modules.forum.dto.UpdatePostDTO;
import com.ulticode.modules.forum.entity.ForumCommunityMember;
import com.ulticode.modules.forum.mapper.ForumCommunityMapper;
import com.ulticode.modules.forum.mapper.ForumCommunityMemberMapper;
import com.ulticode.modules.forum.service.ForumCommentService;
import com.ulticode.modules.forum.service.ForumPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Default (and only) adapter for {@link ForumWritePort}. Delegates post /
 * comment writes to the existing {@code ForumPostService} and
 * {@code ForumCommentService} (preserving their {@code @Transactional} and
 * {@code @CheckBan} guards). Owns the community-membership writes that
 * previously lived in the deleted {@code ForumService} facade.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultForumWritePort implements ForumWritePort {

    private final ForumPostService forumPostService;
    private final ForumCommentService forumCommentService;
    private final ForumCommunityMapper communityMapper;
    private final ForumCommunityMemberMapper memberMapper;
    private final Clock clock;

    @Override
    public ForumPostVO createPost(CreatePostDTO dto, String userId) {
        return forumPostService.createPost(dto, userId);
    }

    @Override
    public ForumPostVO updatePost(String id, UpdatePostDTO dto, String userId) {
        return forumPostService.updatePost(id, dto, userId);
    }

    @Override
    public void deletePost(String id, String userId) {
        forumPostService.deletePost(id, userId);
    }

    @Override
    public void recordShare(String postId) {
        forumPostService.recordShare(postId);
    }

    @Override
    public void recordView(String postId) {
        forumPostService.recordView(postId);
    }

    @Override
    public ForumCommentVO createComment(String postId, CreateCommentDTO dto, String userId) {
        return forumCommentService.createComment(postId, dto, userId);
    }

    @Override
    public ForumCommentVO updateComment(String id, UpdateCommentDTO dto, String userId) {
        return forumCommentService.updateComment(id, dto, userId);
    }

    @Override
    public void deleteComment(String id, String userId) {
        forumCommentService.deleteComment(id, userId);
    }

    @Override
    @Transactional
    public void joinCommunity(String communityId, String userId) {
        if (communityMapper.selectById(communityId) == null) {
            throw new BusinessException(ErrorCode.FORUM_COMMUNITY_NOT_FOUND);
        }
        if (!memberMapper.isMember(communityId, userId)) {
            ForumCommunityMember m = new ForumCommunityMember();
            m.setCommunityId(communityId);
            m.setUserId(userId);
            m.setRole("MEMBER");
            m.setJoinedAt(LocalDateTime.now(clock));
            memberMapper.insert(m);
            communityMapper.incrementMembers(communityId);
        }
    }

    @Override
    @Transactional
    public void leaveCommunity(String communityId, String userId) {
        memberMapper.deleteByCommunityIdAndUserId(communityId, userId);
        communityMapper.decrementMembers(communityId);
    }
}
