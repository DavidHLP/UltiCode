package com.ulticode.common.uuid;

/**
 * Port that generates new entity identifiers. Hides
 * {@link java.util.UUID#randomUUID()} behind one method so unit tests can
 * inject deterministic ids without mocking static calls.
 *
 * <p>Prior to this port, ~33 sites across 14 files called
 * {@code UUID.randomUUID()} inline. Every id-dependent test either
 * skipped the id assertion, captured-and-ignored the value, or skipped
 * the case entirely.
 *
 * <p><strong>Seam justification — two adapters:</strong>
 * <ul>
 *   <li>{@code ProdUuidGenerator} — production, delegates to
 *       {@link java.util.UUID#randomUUID()}; lives in {@code backend-legacy}.</li>
 *   <li>{@code FixedUuidGenerator} — test adapter, returns
 *       deterministic / sequenced ids; lives in {@code backend-legacy}.</li>
 * </ul>
 */
public interface UuidGenerator {

    /**
     * @return a fresh opaque id, suitable for use as a primary key
     *         (typically a UUID-4 string).
     */
    String newId();
}
