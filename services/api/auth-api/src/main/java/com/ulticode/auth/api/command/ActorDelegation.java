package com.ulticode.auth.api.command;

import java.io.Serializable;

/**
 * Actor delegation record carried on every write command issued against
 * {@code backend-auth-api}.
 *
 * <p>Per {@code PROJECT_DOCUMENTATION.md} &sect;6.2, every
 * mutating RPC must capture <i>who is asking</i> and <i>on whose
 * behalf</i>: a write can originate from a human principal (end user /
 * admin), from a back-end service principal (Admin BFF, scheduled job),
 * or from a system event (outbox replay). The producer side needs to
 * know both halves so the auth transaction can write audit, ban and
 * authorization-snapshot history without re-querying the call site.
 *
 * <p>This record deliberately exposes a small shape (no role enum, no
 * permission set): the {@link #actorType()} string is module-defined
 * (e.g. {@code "USER"}, {@code "ADMIN"}, {@code "SERVICE"}) and the
 * identifier is a UUID String. Callers serialise the role / permission
 * intent in {@link #rationale()} when they need audit detail; the auth
 * provider still owns the authoritative role table.
 *
 * <p>Validation rules (enforced by the producer side, not by the record
 * itself &mdash; Java records are data carriers):
 * <ul>
 *   <li>{@link #actorId()} and {@link #delegatorId()} are UUID Strings
 *       (may be the same id when an admin acts for themselves);</li>
 *   <li>{@link #actorType()} is non-blank;</li>
 *   <li>{@link #rationale()} is optional but recommended for
 *       governance-relevant actions (ban, role change, refresh revoke).</li>
 * </ul>
 */
public record ActorDelegation(
        String actorType,
        String actorId,
        String delegatorId,
        String rationale) implements Serializable {
    private static final long serialVersionUID = 1L;


    /**
     * Compact constructor enforcing the non-blank {@link #actorType} rule.
     * Other fields are left nullable so the caller can populate them in
     * the builder style typical for command objects.
     */
    public ActorDelegation {
        if (actorType == null || actorType.isBlank()) {
            throw new IllegalArgumentException(
                    "actorType is required and must be non-blank");
        }
    }
}