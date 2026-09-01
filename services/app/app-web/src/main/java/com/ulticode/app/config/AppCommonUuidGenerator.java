package com.ulticode.app.config;

import com.ulticode.common.uuid.UuidGenerator;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Production UUID generator owned by the App service. */
@Component
public class AppCommonUuidGenerator implements UuidGenerator {

    @Override
    public String newId() {
        return UUID.randomUUID().toString();
    }
}
