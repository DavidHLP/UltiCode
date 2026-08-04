package com.ulticode.auth.api.command;

import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import java.io.Serializable;

/**
 * Command to mutate an account's lifecycle state (active / banned /
 * disabled). Issued by the Admin BFF against {@code backend-auth}
 * {@code AccountAdministrationService.changeState}.
 *
 * <p>Per migration guide &sect;6.2 the command carries
 * {@code commandId}, {@link IdMetadata}, {@link ActorDelegation} and
 * {@link TraceMetadata} via the {@link WriteCommand} base contract.
 * <p>The expected version is the optimistic-lock token: the caller read
 * the snapshot beforehand and pins it here so concurrent writes
 * fail fast rather than overwrite. Required (non-null) for every
 * account-state mutation.
 */
public record ChangeAccountStateCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String accountId,
        Long expectedVersion,
        AccountStateAction action,
        String rationale) implements Serializable, WriteCommand {

    /**
     * Discriminated lifecycle action. The provider must validate the
     * transition (e.g. cannot ban an already-disabled account without
     * an explicit re-enable) and emit the appropriate auth event.
     */
    public enum AccountStateAction {
        /** Soft-disable; account cannot log in but is not banned. */
        DISABLE,
        /** Re-enable a previously disabled account. */
        ENABLE,
        /** Apply a ban with an optional expiry handled by the provider. */
        BAN,
        /** Lift a ban. */
        UNBAN
    }

    public ChangeAccountStateCommand {
        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException(
                    "commandId is required and must be a UUID String");
        }
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException(
                    "accountId is required and must be a UUID String");
        }
        if (idempotency == null) {
            throw new IllegalArgumentException(
                    "idempotency is required (use IdMetadata.mint() when "
                            + "no client token is available)");
        }
        if (actor == null) {
            throw new IllegalArgumentException("actor is required");
        }
        if (action == null) {
            throw new IllegalArgumentException("action is required");
        }
        if (expectedVersion == null) {
            throw new IllegalArgumentException(
                    "expectedVersion is required for optimistic locking");
        }
    }
}