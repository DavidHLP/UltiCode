package com.ulticode.common.uuid;

import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Production {@link UuidGenerator} — delegates to
 * {@link UUID#randomUUID()}. Wired as the default bean by
 * {@link UuidConfig}.
 *
 * @author ulticode
 */
@Component
public class ProdUuidGenerator implements UuidGenerator {

    @Override
    public String newId() {
        return UUID.randomUUID().toString();
    }
}
