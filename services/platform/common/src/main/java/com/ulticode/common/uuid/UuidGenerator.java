package com.ulticode.common.uuid;

/**
 * Port that generates new entity identifiers. Hides
 * {@link java.util.UUID#randomUUID()} behind one method so unit tests can
 * inject deterministic ids without mocking static calls.
 *
 * <p>The production adapter lives in the owning service's application
 * context; the platform module only defines this dependency-free port.
 * {@code FixedUuidGenerator} is a test adapter.
 */
public interface UuidGenerator {

    /**
     * @return a fresh opaque id, suitable for use as a primary key
     *         (typically a UUID-4 string).
     */
    String newId();
}
