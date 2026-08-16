package com.ulticode.submission.config;

import com.ulticode.common.uuid.UuidGenerator;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Production UUID generator for the submission owner runtime.
 *
 * <p>SPLIT-003 slice-2: the owner runtime is a standalone service and cannot
 * rely on App's legacy UUID adapters; it implements the common port directly
 * (same contract as {@code ProdUuidGenerator}: UUID-4 strings).
 */
@Component
public class SubmissionUuidGenerator implements UuidGenerator {

    @Override
    public String newId() {
        return UUID.randomUUID().toString();
    }
}
