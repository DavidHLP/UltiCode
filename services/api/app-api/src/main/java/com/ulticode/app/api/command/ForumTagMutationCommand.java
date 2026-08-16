package com.ulticode.app.api.command;

import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;


/**
 * ADMIN-007: command to apply a single forum-tag mutation owned by
 * {@code backend-app}.
 *
 * <p>Issued by the Admin service's {@code ForumTagHandler} against
 * {@code ForumTagAdministrationService}. Carries the full
 * {@code commandId / idempotency / actor / trace} metadata via the
 * {@link WriteCommand} base contract.
 *
 * @param action      the mutation to apply
 * @param tagId       target tag ID (UPDATE / DELETE)
 * @param sourceTagId source tag ID (MERGE)
 * @param targetTagId target tag ID (MERGE)
 * @param name        new name (CREATE / UPDATE; null keeps the existing)
 * @param slug        new slug (CREATE / UPDATE; null keeps the existing)
 * @param description new description (CREATE / UPDATE; null keeps existing)
 * @param color       new color (CREATE / UPDATE; null keeps existing)
 */
public record ForumTagMutationCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        Action action,
        String tagId,
        String sourceTagId,
        String targetTagId,
        String name,
        String slug,
        String description,
        String color) implements WriteCommand {
    private static final long serialVersionUID = 1L;


    /**
     * Discriminated mutation action for forum tags.
     */
    public enum Action {
        /** Create a new tag. */
        CREATE,
        /** Update fields of an existing tag. */
        UPDATE,
        /** Hard-delete a tag. */
        DELETE,
        /** Delete the source tag after validating the target exists. */
        MERGE
    }

    public ForumTagMutationCommand {
        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException(
                    "commandId is required and must be a UUID String");
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
