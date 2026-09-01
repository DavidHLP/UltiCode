package com.ulticode.judge;

import com.ulticode.common.uuid.UuidGenerator;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Production UUID generator owned by the Judge worker. */
@Component
public class JudgeUuidGenerator implements UuidGenerator {

    @Override
    public String newId() {
        return UUID.randomUUID().toString();
    }
}
