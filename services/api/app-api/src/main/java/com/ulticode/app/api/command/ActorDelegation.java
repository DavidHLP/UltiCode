package com.ulticode.app.api.command;

import java.io.Serializable;

/**
 * Actor delegation record carried on every write command issued
 * against {@code backend-app-api}.
 *
 * <p>Per {@code docs/MICROSERVICE_MIGRATION_GUIDE.md} &sect;6.2, every
 * mutating RPC must capture <i>who is asking</i> and <i>on whose
 * behalf</i>: the Admin BFF and moderation service invoke App's
 * administration / moderation providers to act on user-owned data, but
 * the App transaction still needs to know the human principal behind
 * the action for audit and rate-limit purposes.
 *
 * <p>This record deliberately exposes a small shape (no role enum, no
 * permission set): the {@link #actorType()} string is module-defined
 * (e.g. {@code "ADMIN"}, {@code "MODERATOR"}, {@code "SERVICE"}) and
 * the identifier is a UUID String. The App provider still owns the
 * authoritative role table; commands only carry delegation intent.
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