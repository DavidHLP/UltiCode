package com.ulticode.app.api.service;

import com.ulticode.app.api.command.ForumCommentModerationCommand;
import com.ulticode.app.api.dto.ForumCommentModerationResultDTO;
import com.ulticode.common.rpc.RpcResult;

/**
 * ADMIN-007: app-owned administrative provider for forum comment
 * moderation writes (flag / unflag / soft-delete).
 *
 * <p>Consumed by the Admin service's {@code ForumCommentModerator},
 * which previously reached for {@code ForumCommentMapper} directly.
 * Every call is a mutating RPC, so the command carries the full
 * {@code commandId / idempotency / actor / trace} metadata per the
 * {@link com.ulticode.common.rpc.RpcPolicy} write boundary. The App
 * transaction stays local; the Admin consumer never wraps the call in a
 * local transaction.
 *
 * @author ulticode
 */
public interface ForumCommentAdministrationService {

    /**
     * Apply a single moderation write to a forum comment.
     *
     * @param command the moderation command (action + target + actor
     *                metadata)
     * @return success with the author identity and pre-mutation state
     *         needed by the Admin audit diff; failure with
     *         {@code CONTENT_NOT_FOUND} when the comment is unknown
     */
    RpcResult<ForumCommentModerationResultDTO> moderate(ForumCommentModerationCommand command);
}
