package com.ulticode.app.uuid;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic {@link AppUuidGenerator} for tests. Returns sequential ids
 * prefixed with {@code fixed-uuid-} so assertions can pin generated values
 * without depending on {@link java.util.UUID#randomUUID()}.
 */
public class FixedAppUuidGenerator implements AppUuidGenerator {

    private final AtomicLong counter = new AtomicLong();

    @Override
    public String newId() {
        return "fixed-uuid-" + counter.incrementAndGet();
    }
}
