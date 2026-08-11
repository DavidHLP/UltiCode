package com.ulticode.app.api.dto;

import com.ulticode.app.api.command.ForumCommentModerationCommand;

import java.io.Serializable;

/**
 * ADMIN-007: result of a {@link ForumCommentModerationCommand} carrying
 * everything the Admin audit diff needs without exposing the comment
 * entity.
 *
 * @param commentId           target comment ID
 * @param action              the action that was applied
 * @param authorUserId        author of the comment (null when unknown)
 * @param previousIsFlagged   pre-mutation flagged state (FLAG / UNFLAG)
 * @param previousFlaggedReason pre-mutation flag reason (FLAG / UNFLAG)
 * @param previousIsDeleted   pre-mutation deleted state (DELETE)
 */
public record ForumCommentModerationResultDTO(
        String commentId,
        ForumCommentModerationCommand.Action action,
        String authorUserId,
        boolean previousIsFlagged,
        String previousFlaggedReason,
        boolean previousIsDeleted) implements Serializable {
}
