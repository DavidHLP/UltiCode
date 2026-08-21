package com.ulticode.modules.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Explicit read policy for the App search module. */
@Component
@ConfigurationProperties(prefix = "app.search.read")
public class SearchReadProperties {

    public enum Mode {
        DATABASE,
        INDEXED
    }

    private Mode mode = Mode.DATABASE;
    private boolean fallbackToDatabase;
    /** Null means indexed reads are not explicitly worker-enabled and fail closed. */
    private Boolean workerEnabled;

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public boolean isFallbackToDatabase() {
        return fallbackToDatabase;
    }

    public void setFallbackToDatabase(boolean fallbackToDatabase) {
        this.fallbackToDatabase = fallbackToDatabase;
    }

    public Boolean getWorkerEnabled() {
        return workerEnabled;
    }

    public void setWorkerEnabled(Boolean workerEnabled) {
        this.workerEnabled = workerEnabled;
    }
}
