package com.ulticode.common.uuid;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-test {@link UuidGenerator} — deterministic, monotonically
 * increasing id sequence. Not a {@code @Component}.
 *
 * <p><strong>Not a {@code @Component}.</strong> Production wiring uses
 * {@link ProdUuidGenerator}; this class exists so unit tests can
 * inject a deterministic generator via
 * {@code new FixedUuidGenerator("00000000-0000-0000-0000-000000000001")}
 * (or use the {@link #FixedUuidGenerator()} no-arg constructor to get a
 * sequenced series).
 *
 * @author ulticode
 */
public class FixedUuidGenerator implements UuidGenerator {

    private final AtomicLong counter;
    private final String prefix;
    private final boolean useFixedValue;

    /**
     * Construct a generator that returns a single fixed id — every call
     * to {@link #newId()} returns the same value.
     */
    public FixedUuidGenerator(String fixedId) {
        this.prefix = null;
        this.counter = null;
        this.useFixedValue = true;
        this.fixedValue = fixedId;
    }

    /**
     * Construct a generator that returns a fresh UUID-4 on each call —
     * useful when a test only needs "non-null, never equal to another
     * call".
     */
    public FixedUuidGenerator() {
        this.prefix = null;
        this.counter = null;
        this.useFixedValue = false;
        this.fixedValue = null;
    }

    /**
     * Construct a sequenced generator that returns
     * {@code "test-uuid-1"}, {@code "test-uuid-2"}, ... on each call.
     */
    public static FixedUuidGenerator sequenced(String prefix) {
        return new FixedUuidGenerator(prefix, true);
    }

    private FixedUuidGenerator(String prefix, boolean sequenced) {
        this.prefix = prefix + "-";
        this.counter = new AtomicLong(0L);
        this.useFixedValue = false;
        this.fixedValue = null;
    }

    private final String fixedValue;

    @Override
    public String newId() {
        if (useFixedValue) {
            return fixedValue;
        }
        if (prefix != null) {
            return prefix + counter.incrementAndGet();
        }
        // Fresh UUID-4 — mimics production randomness without Mockito static
        return UUID.randomUUID().toString();
    }
}
