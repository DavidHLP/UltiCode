package com.ulticode.app.api.command;

import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.command.WriteCommand;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;


/**
 * ADMIN-007: command to apply a single moderation write to a forum
 * comment owned by {@code backend-app}.
 *
 * <p>Issued by the Admin service's {@code ForumCommentModerator} against
 * {@code ForumCommentAdministrationService}. Carries the full
 * {@code commandId / idempotency / actor / trace} metadata via the
 * {@link WriteCommand} base contract, matching the write-RPC boundary in
 * {@code docs/MICROSERVICE_MIGRATION_GUIDE.md} &sect;6.2.
 *
 * @param commentId target comment ID
 * @param action    the moderation action to apply
 * @param reason    flag reason (FLAG only; may be null)
 * @param deletedBy admin user ID stamping the soft delete (DELETE only)
 */
public record ForumCommentModerationCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String commentId,
        Action action,
        String reason,
        String deletedBy) implements WriteCommand {
    private static final long serialVersionUID = 1L;


    /**
     * Discriminated moderation action for forum comments.
     */
    public enum Action {
        /** Mark the comment as flagged for review. */
        FLAG,
        /** Clear the flag on a comment. */
        UNFLAG,
        /** Soft-delete the comment; remains in DB for audit. */
        DELETE
    }

    public ForumCommentModerationCommand {
        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException(
                    "commandId is required and must be a UUID String");
        }
        if (commentId == null || commentId.isBlank()) {
            throw new IllegalArgumentException("commentId is required");
        }
        if (action == null) {
            throw new IllegalArgumentException("action is required");
        }
        if (idempotency == null) {
            throw new IllegalArgumentException(
                    "idempotency is required (use IdMetadata.mint() when no "
                            + "client token is available)");
        }
        if (actor == null) {
            throw new IllegalArgumentException("actor is required");
        }
    }
}
