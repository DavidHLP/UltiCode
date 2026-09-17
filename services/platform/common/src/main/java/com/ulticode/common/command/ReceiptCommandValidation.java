package com.ulticode.common.command;

/** Shared command preconditions used before receipt handling. */
public final class ReceiptCommandValidation {

    private ReceiptCommandValidation() {
    }

    /**
     * Validates a delegated owner command: idempotency key, trace, actor id,
     * and delegator id must all be present before a receipt is written.
     */
    public static boolean delegatedCommand(WriteCommand command) {
        return command != null
                && command.idempotency() != null
                && command.idempotency().hasKey()
                && command.trace() != null
                && command.actor() != null
                && command.actor().actorId() != null
                && !command.actor().actorId().isBlank()
                && command.actor().delegatorId() != null
                && !command.actor().delegatorId().isBlank();
    }
}
