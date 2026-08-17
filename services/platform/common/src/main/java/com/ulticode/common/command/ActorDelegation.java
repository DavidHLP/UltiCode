package com.ulticode.common.command;

import java.io.Serializable;

/**
 * Actor delegation carried by write commands across owner boundaries.
 *
 * <p>The shape deliberately contains no role enum or permission set. Each
 * owner validates the actor against its authoritative policy while the
 * command carries who initiated the action and on whose behalf it runs.
 */
public record ActorDelegation(
        String actorType,
        String actorId,
        String delegatorId,
        String rationale) implements Serializable {
    private static final long serialVersionUID = 1L;

    public ActorDelegation {
        if (actorType == null || actorType.isBlank()) {
            throw new IllegalArgumentException(
                    "actorType is required and must be non-blank");
        }
    }
}
