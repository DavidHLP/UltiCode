package com.ulticode.app.api.command;

import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;

import java.io.Serializable;

/**
 * ADMIN-007: command for App-owned forum-post moderation fields.
 *
 * <p>The provider validates the delegated admin actor and applies the
 * mutation inside the App-owned transaction. The raw {@code ForumOwnerPort}
 * remains an internal App port and is not exported over Dubbo.
 *
 * @param postId target forum post
 * @param action field mutation to apply
 * @param reason flag reason for FLAG; ignored for other actions
 */
public record ForumPostModerationCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String postId,
        Action action,
        String reason) implements Serializable, WriteCommand {

    public enum Action {
        FLAG,
        UNFLAG,
        PIN,
        UNPIN,
        LOCK,
        UNLOCK
    }

    public ForumPostModerationCommand {
        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException("commandId is required");
        }
        if (postId == null || postId.isBlank()) {
            throw new IllegalArgumentException("postId is required");
        }
        if (action == null) {
            throw new IllegalArgumentException("action is required");
        }
        if (idempotency == null) {
            throw new IllegalArgumentException("idempotency is required");
        }
        if (actor == null) {
            throw new IllegalArgumentException("actor is required");
        }
    }
}
