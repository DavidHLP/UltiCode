package com.ulticode.app.api.command;

import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;

/**
 * Command to apply a moderation decision to App-owned content
 * (forum post, solution, comment, problem note, etc.).
 *
 * <p>Issued by the Admin / Moderation service against
 * {@code backend-app} {@code ContentModerationService.apply}.
 *
 * <p>Per migration guide &sect;6.2 the command carries
 * {@code commandId}, {@link IdMetadata}, {@link ActorDelegation} and
 * {@link TraceMetadata} via the {@link WriteCommand} base contract.
 *
 * <p>The {@code Admin} side keeps authoritative moderation case
 * records (its owned table {@code moderation_queue},
 * {@code moderation_actions}); the App side merely enforces the
 * lifecycle effect on the targeted content and returns the new state.
 */
public record ApplyModerationCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String moderationCaseId,
        String contentId,
        String contentType,
        ModerationAction action,
        String rationale) implements WriteCommand {

    /**
     * Discriminated moderation action. Provider must validate the
     * transition (e.g. cannot hide an already-deleted post without
     * an explicit restore) and emit the appropriate App event.
     */
    public enum ModerationAction {
        /** Mark the content as hidden; remains in DB but not user-visible. */
        HIDE,
        /** Restore a previously hidden content item. */
        RESTORE,
        /** Soft-delete the content; remains in DB for audit. */
        DELETE,
        /** Undelete a previously soft-deleted content item. */
        UNDELETE
    }

    public ApplyModerationCommand {
        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException(
                    "commandId is required and must be a UUID String");
        }
        if (moderationCaseId == null || moderationCaseId.isBlank()) {
            throw new IllegalArgumentException(
                    "moderationCaseId is required and must be a UUID String");
        }
        if (contentId == null || contentId.isBlank()) {
            throw new IllegalArgumentException(
                    "contentId is required and must be a UUID String");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException(
                    "contentType is required (e.g. forum_post, solution)");
        }
        if (action == null) {
            throw new IllegalArgumentException("action is required");
        }
        if (idempotency == null) {
            throw new IllegalArgumentException(
                    "idempotency is required (use IdMetadata.mint() when "
                            + "no client token is available)");
        }
        if (actor == null) {
            throw new IllegalArgumentException("actor is required");
        }
    }
}