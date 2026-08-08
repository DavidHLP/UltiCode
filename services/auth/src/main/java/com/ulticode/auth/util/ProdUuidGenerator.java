package com.ulticode.auth.util;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Production implementation of {@link UuidGenerator}.
 */
@Component
public class ProdUuidGenerator implements UuidGenerator {

    @Override
    public String newId() {
        return UUID.randomUUID().toString();
    }
}
