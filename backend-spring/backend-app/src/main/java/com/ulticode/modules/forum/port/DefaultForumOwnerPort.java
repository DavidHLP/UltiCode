package com.ulticode.modules.forum.port;

import com.ulticode.app.api.service.ForumOwnerPort;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Default implementation of {@link ForumOwnerPort}.
 *
 * <p>Located in {@code backend-app} (forum implementation module) so it
 * can use the forum entity and mapper directly. Spring component scan
 * makes the bean available to any consumer that injects {@link ForumOwnerPort}.
 *
 * <p>P7-RELOCATE-FORUM-001: relocated from {@code backend-legacy} when the
 * forum family moved to {@code backend-app}.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultForumOwnerPort implements ForumOwnerPort {

    private final ForumPostMapper forumPostMapper;

    @Override
    @Transactional
    public FlagResult flagPost(String postId, String reason, LocalDateTime flaggedAt) {
        ForumPost post = loadOrThrow(postId);

        boolean previousIsFlagged = Boolean.TRUE.equals(post.getIsFlagged());
        String previousReason = post.getFlaggedReason() != null ? post.getFlaggedReason() : "";

        post.setIsFlagged(true);
        post.setFlaggedReason(reason != null ? reason : "");
        post.setFlaggedAt(flaggedAt);
        forumPostMapper.updateById(post);
        log.info("Flagged post {}", postId);

        return new FlagResult(post.getUserId(), previousIsFlagged, previousReason);
    }

    @Override
    @Transactional
    public FlagResult unflagPost(String postId) {
        ForumPost post = loadOrThrow(postId);

        boolean previousIsFlagged = Boolean.TRUE.equals(post.getIsFlagged());
        String previousReason = post.getFlaggedReason() != null ? post.getFlaggedReason() : "";

        post.setIsFlagged(false);
        post.setFlaggedReason(null);
        post.setFlaggedAt(null);
        forumPostMapper.updateById(post);
        log.info("Unflagged post {}", postId);

        return new FlagResult(post.getUserId(), previousIsFlagged, previousReason);
    }

    @Override
    @Transactional
    public ToggleResult setPinned(String postId, boolean pinned) {
        ForumPost post = loadOrThrow(postId);
        boolean previous = Boolean.TRUE.equals(post.getIsPinned());
        post.setIsPinned(pinned);
        forumPostMapper.updateById(post);
        log.info("Set post {} pinned={}", postId, pinned);
        return new ToggleResult(post.getUserId(), previous);
    }

    @Override
    @Transactional
    public ToggleResult setLocked(String postId, boolean locked) {
        ForumPost post = loadOrThrow(postId);
        boolean previous = Boolean.TRUE.equals(post.getIsLocked());
        post.setIsLocked(locked);
        forumPostMapper.updateById(post);
        log.info("Set post {} locked={}", postId, locked);
        return new ToggleResult(post.getUserId(), previous);
    }

    private ForumPost loadOrThrow(String postId) {
        ForumPost post = forumPostMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND);
        }
        return post;
    }

    @Override
    public String resolveAuthorId(String postId) {
        ForumPost post = forumPostMapper.selectById(postId);
        return post != null ? post.getUserId() : null;
    }
}
