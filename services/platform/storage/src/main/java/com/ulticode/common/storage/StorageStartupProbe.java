package com.ulticode.common.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;

/**
 * Mandatory object-store startup gate.
 *
 * <p>Runs during context refresh &mdash; before the servlet connector accepts traffic &mdash; so a context that is able
 * to serve requests has already seen a successful bounded probe of the configured bucket. Exhausting the attempts
 * fails the refresh (and therefore the boot): there is no local-disk fallback and no "serve first, discover later"
 * window, and the owner readiness endpoints report the recorded state instead of assuming success.
 */
@Slf4j
public class StorageStartupProbe implements InitializingBean {

    private final S3Storage storage;
    private final StorageProperties properties;
    private final StorageReadiness readiness;

    public StorageStartupProbe(S3Storage storage, StorageProperties properties, StorageReadiness readiness) {
        this.storage = storage;
        this.properties = properties;
        this.readiness = readiness;
    }

    @Override
    public void afterPropertiesSet() {
        StorageProperties.StartupProbe probe = properties.getStartupProbe();
        if (!probe.isEnabled()) {
            log.warn("Object-store startup probe is disabled (app.storage.startup-probe.enabled=false); "
                    + "RustFS availability is not verified at boot");
            readiness.markSkipped();
            return;
        }

        StorageException last = null;
        for (int attempt = 1; attempt <= probe.getAttempts(); attempt++) {
            try {
                storage.probe();
                readiness.markReady();
                return;
            } catch (StorageException exception) {
                last = exception;
                if (attempt < probe.getAttempts() && probe.getDelayMs() > 0) {
                    try {
                        Thread.sleep(probe.getDelayMs());
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        readiness.markFailed("interrupted");
                        throw new StorageException("Object-store startup probe interrupted", interrupted);
                    }
                }
            }
        }

        readiness.markFailed(last == null ? "unknown failure" : String.valueOf(last.getMessage()));
        throw new StorageException("Object-store startup probe failed after " + probe.getAttempts() + " attempts", last);
    }
}
