package com.ulticode.modules.admin.policy.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.util.AuditHelper;
import com.ulticode.modules.admin.policy.ForumFlagPolicy;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Default {@link ForumFlagPolicy} implementation.
 *
 * <p>Flagging captures the moderation reason and stamps the wall-clock time
 * via the injected {@link Clock}, mirroring the prior inline behaviour in
 * {@code AdminForumServiceImpl.flagPost} / {@code unflagPost}.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForumFlagPolicyImpl implements ForumFlagPolicy {

    /** Audit entity type — kept in sync with the legacy toggle methods. */
    private static final String ENTITY_FORUM_POST = "FORUM_POST";

    /** Audit action constant for flag operations. */
    private static final String ACTION_FLAG_POST = "FLAG_POST";

    /** Audit action constant for unflag operations. */
    private static final String ACTION_UNFLAG_POST = "UNFLAG_POST";

    private final ForumPostMapper forumPostMapper;
    private final AuditHelper auditHelper;
    private final Clock clock;

    /**
     * Flag the post for moderation.
     *
     * @param postId target post id
     * @param reason human-readable reason (may be {@code null}, stored as empty string)
     * @throws BusinessException with {@code NOT_FOUND} when the post does not exist
     */
    @Override
    public void flag(String postId, String reason) {
        ForumPost post = loadOrThrow(postId);
        String normalisedReason = reason != null ? reason : "";

        auditHelper.logForUser(
            ACTION_FLAG_POST,
            ENTITY_FORUM_POST,
            postId,
            post.getUserId(),
            Map.of(
                "isFlagged", Boolean.TRUE.equals(post.getIsFlagged()),
                "flaggedReason", post.getFlaggedReason() != null ? post.getFlaggedReason() : ""
            ),
            Map.of(
                "isFlagged", true,
                "flaggedReason", normalisedReason
            )
        );

        post.setIsFlagged(true);
        post.setFlaggedReason(reason);
        post.setFlaggedAt(LocalDateTime.now(clock));
        forumPostMapper.updateById(post);
        log.info("Post flagged: {} reason: {}", postId, reason);
    }

    /**
     * Remove the moderation flag from the post.
     *
     * @param postId target post id
     * @throws BusinessException with {@code NOT_FOUND} when the post does not exist
     */
    @Override
    public void unflag(String postId) {
        ForumPost post = loadOrThrow(postId);

        auditHelper.logForUser(
            ACTION_UNFLAG_POST,
            ENTITY_FORUM_POST,
            postId,
            post.getUserId(),
            Map.of(
                "isFlagged", Boolean.TRUE.equals(post.getIsFlagged()),
                "flaggedReason", post.getFlaggedReason() != null ? post.getFlaggedReason() : ""
            ),
            Map.of(
                "isFlagged", false,
                "flaggedReason", ""
            )
        );

        post.setIsFlagged(false);
        post.setFlaggedReason(null);
        post.setFlaggedAt(null);
        forumPostMapper.updateById(post);
        log.info("Post unflagged: {}", postId);
    }

    /**
     * Load the post or throw {@link BusinessException} with {@code NOT_FOUND}.
     *
     * @param postId target post id
     * @return loaded post entity (never {@code null})
     */
    private ForumPost loadOrThrow(String postId) {
        ForumPost post = forumPostMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return post;
    }
}