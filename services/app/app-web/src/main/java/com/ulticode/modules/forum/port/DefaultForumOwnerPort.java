package com.ulticode.modules.forum.port;

import com.ulticode.app.api.command.ForumPostModerationCommand;
import com.ulticode.app.api.dto.ForumPostModerationResultDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.modules.forum.port.ForumOwnerPort;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Default implementation of {@link ForumOwnerPort}.
 *
 * <p>This is the local App owner port. Its command dispatch is used by the
 * command-based Dubbo provider; the raw port itself is not exported.
 *
 * @author ulticode
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class DefaultForumOwnerPort implements ForumOwnerPort {

    private final ForumPostMapper forumPostMapper;
    private final com.ulticode.modules.search.source.SearchDocumentChangedPublisher searchPublisher;

    @Override
    @Transactional
    public FlagResult flagPost(String postId, String reason, LocalDateTime flaggedAt) {
        ForumPost post = loadOrThrow(postId);
        boolean previousIsFlagged = Boolean.TRUE.equals(post.getIsFlagged());
        String previousReason = post.getFlaggedReason() != null ? post.getFlaggedReason() : "";

        int updated = forumPostMapper.updateFlagStatusAt(
                postId, true, reason != null ? reason : "",
                flaggedAt != null ? flaggedAt : LocalDateTime.now());
        ensureStillPresent(postId, updated);
        log.info("Flagged post {}", postId);
        return new FlagResult(post.getUserId(), previousIsFlagged, previousReason);
    }

    @Override
    @Transactional
    public FlagResult unflagPost(String postId) {
        ForumPost post = loadOrThrow(postId);
        boolean previousIsFlagged = Boolean.TRUE.equals(post.getIsFlagged());
        String previousReason = post.getFlaggedReason() != null ? post.getFlaggedReason() : "";

        int updated = forumPostMapper.updateFlagStatusAt(postId, false, null, null);
        ensureStillPresent(postId, updated);
        log.info("Unflagged post {}", postId);
        return new FlagResult(post.getUserId(), previousIsFlagged, previousReason);
    }

    @Override
    @Transactional
    public ToggleResult setPinned(String postId, boolean pinned) {
        ForumPost post = loadOrThrow(postId);
        boolean previous = Boolean.TRUE.equals(post.getIsPinned());
        int updated = forumPostMapper.updatePinStatus(postId, pinned);
        ensureStillPresent(postId, updated);
        log.info("Set post {} pinned={}", postId, pinned);
        return new ToggleResult(post.getUserId(), previous);
    }

    @Override
    @Transactional
    public ToggleResult setLocked(String postId, boolean locked) {
        ForumPost post = loadOrThrow(postId);
        boolean previous = Boolean.TRUE.equals(post.getIsLocked());
        int updated = forumPostMapper.updateLockStatus(postId, locked);
        ensureStillPresent(postId, updated);
        log.info("Set post {} locked={}", postId, locked);
        return new ToggleResult(post.getUserId(), previous);
    }

    /** Apply a validated command while keeping mapper access in this owner. */
    @Transactional
    public RpcResult<ForumPostModerationResultDTO> moderate(ForumPostModerationCommand command) {
        String traceId = command.trace() != null ? command.trace().traceId() : null;
        try {
            return switch (command.action()) {
                case FLAG -> {
                    FlagResult result = flagPost(command.postId(), command.reason(), LocalDateTime.now());
                    yield RpcResult.success(toResult(command, result.authorUserId(),
                            result.previousIsFlagged(), result.previousReason()), traceId);
                }
                case UNFLAG -> {
                    FlagResult result = unflagPost(command.postId());
                    yield RpcResult.success(toResult(command, result.authorUserId(),
                            result.previousIsFlagged(), result.previousReason()), traceId);
                }
                case PIN -> {
                    ToggleResult result = setPinned(command.postId(), true);
                    yield RpcResult.success(toResult(command, result.authorId(),
                            result.previousState(), null), traceId);
                }
                case UNPIN -> {
                    ToggleResult result = setPinned(command.postId(), false);
                    yield RpcResult.success(toResult(command, result.authorId(),
                            result.previousState(), null), traceId);
                }
                case LOCK -> {
                    ToggleResult result = setLocked(command.postId(), true);
                    yield RpcResult.success(toResult(command, result.authorId(),
                            result.previousState(), null), traceId);
                }
                case UNLOCK -> {
                    ToggleResult result = setLocked(command.postId(), false);
                    yield RpcResult.success(toResult(command, result.authorId(),
                            result.previousState(), null), traceId);
                }
            };
        } catch (BusinessException exception) {
            if (BaseErrorCode.NOT_FOUND.equals(exception.getErrorCode())) {
                return RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, traceId);
            }
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        }
    }

    private static ForumPostModerationResultDTO toResult(
            ForumPostModerationCommand command,
            String authorUserId,
            boolean previousState,
            String previousReason) {
        return new ForumPostModerationResultDTO(
                command.postId(), command.action(), authorUserId, previousState, previousReason);
    }

    private ForumPost loadOrThrow(String postId) {
        ForumPost post = forumPostMapper.selectByIdForUpdateIgnoreDeleted(postId);
        if (post == null || Boolean.TRUE.equals(post.getIsDeleted())) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND);
        }
        return post;
    }

    private void ensureStillPresent(String postId, int updated) {
        if (updated == 0) {
            ForumPost current = forumPostMapper.selectByIdForUpdateIgnoreDeleted(postId);
            if (current == null || Boolean.TRUE.equals(current.getIsDeleted())) {
                throw new BusinessException(BaseErrorCode.NOT_FOUND);
            }
            throw new BusinessException(BaseErrorCode.CONFLICT);
        }
    }

    @Override
    public String resolveAuthorId(String postId) {
        ForumPost post = forumPostMapper.selectById(postId);
        return post != null ? post.getUserId() : null;
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public DeleteResult deletePost(String postId) {
        return deletePost(postId, null);
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public DeleteResult deletePost(String postId, String deletedBy) {
        ForumPost post = forumPostMapper.selectByIdForUpdateIgnoreDeleted(postId);
        if (post == null || Boolean.TRUE.equals(post.getIsDeleted())) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND);
        }
        int updated = forumPostMapper.softDelete(postId, deletedBy);
        if (updated == 0) {
            ForumPost current = forumPostMapper.selectByIdForUpdateIgnoreDeleted(postId);
            if (current == null || Boolean.TRUE.equals(current.getIsDeleted())) {
                throw new BusinessException(BaseErrorCode.NOT_FOUND);
            }
            throw new BusinessException(BaseErrorCode.CONFLICT);
        }
        searchPublisher.publishForumPost(post, false);
        log.info("Soft-deleted forum post {}", postId);
        return new DeleteResult(post.getUserId(), post.getTitle());
    }
}
