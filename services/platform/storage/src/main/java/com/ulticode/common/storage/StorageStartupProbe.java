package com.ulticode.common.storage;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/** Runs a bounded S3 readiness check after the application is ready to serve. */
public class StorageStartupProbe {

    private final S3Storage storage;
    private final StorageProperties properties;

    public StorageStartupProbe(S3Storage storage, StorageProperties properties) {
        this.storage = storage;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verify() {
        StorageProperties.StartupProbe probe = properties.getStartupProbe();
        if (!probe.isEnabled()) {
            return;
        }
        StorageException last = null;
        for (int attempt = 1; attempt <= probe.getAttempts(); attempt++) {
            try {
                storage.probe();
                return;
            } catch (StorageException exception) {
                last = exception;
                if (attempt < probe.getAttempts() && probe.getDelayMs() > 0) {
                    try {
                        Thread.sleep(probe.getDelayMs());
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new StorageException("Object-store startup probe interrupted", interrupted);
                    }
                }
            }
        }
        throw new StorageException("Object-store startup probe failed after " + probe.getAttempts() + " attempts", last);
    }
}
