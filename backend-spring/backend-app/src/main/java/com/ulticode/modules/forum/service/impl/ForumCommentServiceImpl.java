package com.ulticode.modules.forum.service.impl;

import com.ulticode.app.error.ForumErrorCode;
import com.ulticode.app.security.CheckBan;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.forum.dto.CreateCommentDTO;
import com.ulticode.modules.forum.dto.ForumCommentVO;
import com.ulticode.modules.forum.dto.UpdateCommentDTO;
import com.ulticode.modules.forum.entity.ForumComment;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.lifecycle.ForumUserLifecyclePort;
import com.ulticode.modules.forum.mapper.ForumCommentMapper;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import com.ulticode.modules.forum.port.ForumUserReadPort;
import com.ulticode.modules.forum.projection.ForumCommentProjection;
import com.ulticode.modules.forum.service.ForumCommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of ForumCommentService.
 * Handles all forum comment operations.
 *
 * <p>P7-RELOCATE-FORUM-001: {@code UserReadProjection} replaced with
 * {@link ForumUserReadPort}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForumCommentServiceImpl implements ForumCommentService {

    private final ForumCommentMapper commentMapper;
    private final ForumPostMapper postMapper;
    private final ForumUserLifecyclePort forumUserLifecycle;
    private final ForumUserReadPort forumUserReadPort;
    private final ForumCommentProjection commentProjection;
    private final Clock clock;

    @Override
    @Transactional
    @CheckBan
    public ForumCommentVO createComment(String postId, CreateCommentDTO dto, String userId) {
        log.debug("Creating comment on post: {} for user: {}", postId, userId);

        ForumPost post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(ForumErrorCode.FORUM_POST_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(post.getIsLocked())) {
            throw new BusinessException(ForumErrorCode.FORUM_POST_LOCKED);
        }

        String forumUserId = forumUserLifecycle.resolveOrCreate(userId).getId();

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

        Map<String, ForumUserReadPort.UserSummary> authorMap = new HashMap<>();
        ForumUserReadPort.UserSummary author = forumUserReadPort.findById(userId);
        if (author != null) authorMap.put(userId, author);

        return commentProjection.toCommentVO(comment, authorMap);
    }

    @Override
    @Transactional
    public ForumCommentVO updateComment(String id, UpdateCommentDTO dto, String userId) {
        log.debug("Updating comment: {} for user: {}", id, userId);

        ForumComment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new BusinessException(ForumErrorCode.FORUM_COMMENT_NOT_FOUND);
        }
        if (!comment.getAuthorId().equals(userId)) {
            throw new BusinessException(ForumErrorCode.FORUM_CANNOT_EDIT_POST);
        }

        comment.setBody(dto.getBody());
        comment.setMarkdown(dto.getBody());
        // Sync editedAt to in-memory object so the projection VO returns the new
        // timestamp instead of the pre-existing null. markAsEdited below only
        // updates the DB; the memory copy needs the explicit set for VO mapping.
        comment.setEditedAt(LocalDateTime.now(clock));
        commentMapper.updateById(comment);
        commentMapper.markAsEdited(id);

        Map<String, ForumUserReadPort.UserSummary> authorMap = new HashMap<>();
        ForumUserReadPort.UserSummary author = forumUserReadPort.findById(comment.getAuthorId());
        if (author != null) authorMap.put(comment.getAuthorId(), author);

        return commentProjection.toCommentVO(comment, authorMap);
    }

    @Override
    @Transactional
    public void deleteComment(String id, String userId) {
        log.debug("Deleting comment: {} for user: {}", id, userId);

        ForumComment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new BusinessException(ForumErrorCode.FORUM_COMMENT_NOT_FOUND);
        }
        if (!comment.getAuthorId().equals(userId)) {
            throw new BusinessException(ForumErrorCode.FORUM_CANNOT_DELETE_POST);
        }

        commentMapper.softDelete(id, userId);
    }
}
