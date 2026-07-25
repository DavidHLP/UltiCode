package com.ulticode.app.api.command;

import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;

/**
 * Base contract every write command issued against
 * {@code backend-app-api} must implement.
 *
 * <p>Per {@code docs/MICROSERVICE_MIGRATION_GUIDE.md} &sect;6.2 the
 * migration guide mandates that each write Command carries:
 * <ul>
 *   <li>a {@code commandId / idempotencyKey} so retried producers do
 *       not double-apply the side effect;</li>
 *   <li>expected version (where applicable);</li>
 *   <li>an explicit actor delegation (who / on whose behalf);</li>
 *   <li>trace metadata so deadlines propagate through Dubbo unchanged.</li>
 * </ul>
 *
 * <p>Concrete commands embed the metadata via {@link #idempotency()},
 * {@link #actor()}, {@link #trace()} and {@link #commandId()}. The
 * shape is intentionally narrow (no business fields).
 */
public interface WriteCommand {

    /**
     * @return a stable command id (UUID String) used for log
     *         correlation and provider-side dedup. May equal
     *         {@code idempotency().idempotencyKey()} when the caller
     *         chooses to reuse the same token, but is not required to.
     */
    String commandId();

    /**
     * @return idempotency metadata (token + optional fingerprint).
     *         Required so the App provider can dedupe replays.
     */
    IdMetadata idempotency();

    /**
     * @return the actor who initiated the write (human or service).
     */
    ActorDelegation actor();

    /**
     * @return trace metadata so the producer can stamp the audit
     *         record and surface deadline propagation. Never null;
     *         producers accept {@link TraceMetadata#EMPTY}.
     */
    TraceMetadata trace();
}