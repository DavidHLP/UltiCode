package com.ulticode.app.uuid;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * App-side UUID generator for entity identifiers.
 *
 * <p>Replaces the legacy {@code com.ulticode.common.uuid.UuidGenerator} /
 * {@code ProdUuidGenerator} pair. Same contract: hides
 * {@link UUID#randomUUID()} behind a single method so tests can inject
 * deterministic ids.
 *
 * <p>P7-RELOCATE-SOLUTION-001: required when backend-app stopped depending
 * on backend-legacy.
 */
public interface AppUuidGenerator {

    /**
     * @return a fresh opaque id, suitable for use as a primary key
     *         (typically a UUID-4 string).
     */
    String newId();

    /**
     * Production implementation — delegates to {@link UUID#randomUUID()}.
     */
    @Component
    class ProdAppUuidGenerator implements AppUuidGenerator {
        @Override
        public String newId() {
            return UUID.randomUUID().toString();
        }
    }
}
