package com.ulticode.auth.util;

/**
 * Deterministic test adapter for {@link UuidGenerator}.
 */
public class FixedUuidGenerator implements UuidGenerator {

    private final String fixedId;

    public FixedUuidGenerator() {
        this("fixed-uuid-1234");
    }

    public FixedUuidGenerator(String fixedId) {
        this.fixedId = fixedId;
    }

    @Override
    public String newId() {
        return fixedId;
    }
}
