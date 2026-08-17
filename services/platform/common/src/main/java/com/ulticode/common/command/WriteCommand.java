package com.ulticode.common.command;

import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;

import java.io.Serializable;

/**
 * Common metadata contract for owner-bound write commands.
 *
 * <p>Concrete commands carry a stable command id, idempotency metadata,
 * actor delegation and trace metadata. Business fields stay in the owner
 * contract that defines the command.
 */
public interface WriteCommand extends Serializable {

    /** @return stable command id used for correlation and provider deduplication. */
    String commandId();

    /** @return idempotency metadata required for replay-safe writes. */
    IdMetadata idempotency();

    /** @return actor who initiated the write, human or service. */
    ActorDelegation actor();

    /** @return trace metadata; producers use {@link TraceMetadata#EMPTY} when absent. */
    TraceMetadata trace();
}
