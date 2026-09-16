package com.ulticode.common.command;

import java.io.Serializable;

/** Metadata projected from an owner command for receipt persistence. */
public record ReceiptCommandMetadata(
        String commandId,
        String idempotencyKey,
        String traceId,
        String actorType,
        String actorId) implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Projects metadata from the shared command contract. */
    public static ReceiptCommandMetadata from(WriteCommand command) {
        if (command == null) {
            return null;
        }
        ActorDelegation actor = command.actor();
        return new ReceiptCommandMetadata(
                command.commandId(),
                command.idempotency() == null ? null : command.idempotency().idempotencyKey(),
                command.trace() == null ? null : command.trace().traceId(),
                actor == null ? null : actor.actorType(),
                actor == null ? null : actor.actorId());
    }

    /** @return whether this projection has the key needed for receipt lookup. */
    public boolean hasIdempotencyKey() {
        return idempotencyKey != null && !idempotencyKey.isBlank();
    }
}
