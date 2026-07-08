package com.ulticode.common.uuid;

/**
 * Port that generates new entity identifiers. Hides
 * {@link java.util.UUID#randomUUID()} behind one method so unit tests can
 * inject deterministic ids without mocking static calls.
 *
 * <p>Prior to this port, ~33 sites across 14 files called
 * {@code UUID.randomUUID()} inline. Every id-dependent test either
 * skipped the id assertion, captured-and-ignored the value, or skipped
 * the case entirely. See
 * {@code /tmp/architecture-review-1783485814.html} candidate 7.
 *
 * <p><strong>Seam justification — two adapters:</strong>
 * <ul>
 *   <li>{@link ProdUuidGenerator} — production, delegates to
 *       {@link java.util.UUID#randomUUID()}.</li>
 *   <li>{@link FixedUuidGenerator} — test adapter, returns
 *       deterministic / sequenced ids; not a {@code @Component}.</li>
 * </ul>
 *
 * <p>Mirrors the proven {@code ClockConfig}/{@code RateLimiter} pattern.
 *
 * @author ulticode
 */
public interface UuidGenerator {

    /**
     * @return a fresh opaque id, suitable for use as a primary key
     *         (typically a UUID-4 string).
     */
    String newId();
}
