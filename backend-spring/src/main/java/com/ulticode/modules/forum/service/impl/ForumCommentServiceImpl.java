package com.ulticode.modules.forum.service.impl;

import com.ulticode.common.annotation.CheckBan;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.forum.dto.CreateCommentDTO;
import com.ulticode.modules.forum.dto.ForumCommentVO;
import com.ulticode.modules.forum.dto.UpdateCommentDTO;
import com.ulticode.modules.forum.entity.ForumComment;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.entity.ForumUser;
import com.ulticode.modules.forum.mapper.ForumCommentMapper;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import com.ulticode.modules.forum.mapper.ForumUserMapper;
import com.ulticode.modules.forum.service.ForumCommentService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of ForumCommentService.
 * Handles all forum comment operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForumCommentServiceImpl implements ForumCommentService {

    private final ForumCommentMapper commentMapper;
    private final ForumPostMapper postMapper;
    private final ForumUserMapper forumUserMapper;
    private final UserService userService;
    private final Clock clock;

    @Override
    @Transactional
    @CheckBan
    public ForumCommentVO createComment(String postId, CreateCommentDTO dto, String userId) {
        log.debug("Creating comment on post: {} for user: {}", postId, userId);

        ForumPost post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.FORUM_POST_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(post.getIsLocked())) {
            throw new BusinessException(ErrorCode.FORUM_POST_LOCKED);
        }

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

        Map<String, User> authorMap = new HashMap<>();
        userService.findById(userId).ifPresent(user -> authorMap.put(userId, user));

        return convertToCommentVO(comment, authorMap);
    }

    @Override
    @Transactional
    public ForumCommentVO updateComment(String id, UpdateCommentDTO dto, String userId) {
        log.debug("Updating comment: {} for user: {}", id, userId);

        ForumComment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new BusinessException(ErrorCode.FORUM_COMMENT_NOT_FOUND);
        }
        if (!comment.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORUM_CANNOT_EDIT_POST);
        }

        comment.setBody(dto.getBody());
        comment.setMarkdown(dto.getBody());
        // Sync editedAt to in-memory object so convertToCommentVO returns the new
        // timestamp instead of the pre-existing null. markAsEdited below only
        // updates the DB; the memory copy needs the explicit set for VO mapping.
        comment.setEditedAt(LocalDateTime.now(clock));
        commentMapper.updateById(comment);
        commentMapper.markAsEdited(id);

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
        if (!comment.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORUM_CANNOT_DELETE_POST);
        }

        commentMapper.softDelete(id, userId);
    }

    @Override
    public ForumCommentVO convertToCommentVO(ForumComment comment, Map<String, User> authorMap) {
        ForumCommentVO vo = new ForumCommentVO();
        vo.setId(comment.getId());
        vo.setPostId(comment.getPostId());
        vo.setParentId(comment.getParentId());
        vo.setAuthorId(comment.getAuthorId());

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

    @Override
    public List<ForumCommentVO> buildCommentTree(
            List<? extends ForumComment> comments,
            Map<String, User> authorMap) {

        List<ForumComment> topLevel = comments.stream()
                .filter(c -> c.getParentId() == null)
                .collect(Collectors.toList());

        return topLevel.stream()
                .map(c -> {
                    ForumCommentVO vo = convertToCommentVO(c, authorMap);
                    List<ForumCommentVO> replies = findReplies(c.getId(), comments, authorMap);
                    if (!replies.isEmpty()) {
                        vo.setReplies(replies);
                    }
                    return vo;
                })
                .collect(Collectors.toList());
    }

    private List<ForumCommentVO> findReplies(
            String parentId,
            List<? extends ForumComment> allComments,
            Map<String, User> authorMap) {

        return allComments.stream()
                .filter(c -> parentId.equals(c.getParentId()))
                .map(c -> {
                    ForumCommentVO vo = convertToCommentVO(c, authorMap);
                    vo.setReplies(findReplies(c.getId(), allComments, authorMap));
                    return vo;
                })
                .collect(Collectors.toList());
    }

    private String ensureForumUserExists(String userId) {
        ForumUser forumUser = forumUserMapper.selectById(userId);
        if (forumUser != null) {
            return forumUser.getId();
        }

        User user = userService.findById(userId)
                .orElseThrow(() -> {
                    log.error("User not found when creating forum user: {}", userId);
                    return new BusinessException(ErrorCode.USER_NOT_FOUND);
                });

        ForumUser newForumUser = new ForumUser();
        newForumUser.setId(userId);
        newForumUser.setUsername(user.getUsername());
        newForumUser.setAvatar(user.getAvatar());
        newForumUser.setKarma(0);
        newForumUser.setCreatedAt(LocalDateTime.now(clock));

        forumUserMapper.insert(newForumUser);
        log.debug("Created forum user entry for user: {} with id: {}", user.getUsername(), userId);

        return newForumUser.getId();
    }
}
