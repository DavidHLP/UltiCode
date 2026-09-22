package com.ulticode.app.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Records durable cleanup intents for replaced avatar objects. Callers invoke
 * {@link #enqueue(String)} inside the profile mutation transaction so the
 * intent commits or rolls back together with the row change.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageCleanupOutbox {

    private final StorageCleanupOutboxMapper outboxMapper;

    public void enqueueAfterGrace(String objectKey, int delaySeconds) {
        try {
            outboxMapper.enqueueDelayed(UUID.randomUUID().toString(), objectKey, delaySeconds);
        } catch (DuplicateKeyException exception) {
            if (outboxMapper.reopenTerminalRow(objectKey) == 0) {
                log.debug("Delayed storage cleanup intent for {} already queued", objectKey);
            }
        }
    }

    public void enqueue(String objectKey) {
        StorageCleanupOutboxRecord record = new StorageCleanupOutboxRecord();
        record.setObjectKey(objectKey);
        record.setState("PENDING");
        record.setAttempts(0);
        try {
            outboxMapper.insert(record);
        } catch (DuplicateKeyException exception) {
            // A terminal row for the same key may exist because legacy avatar
            // names repeat across replacements; reopen it so the intent is
            // never silently dropped. A PENDING/CLAIMED row still covers it.
            if (outboxMapper.reopenTerminalRow(objectKey) == 0) {
                log.debug("Storage cleanup intent for {} already queued", objectKey);
            }
        }
    }
}
