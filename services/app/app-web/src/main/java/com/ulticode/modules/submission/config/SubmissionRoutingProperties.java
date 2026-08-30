package com.ulticode.modules.submission.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.function.Supplier;

/** Gates the owner read route and the explicit legacy rollback projection. */
@Configuration
@ConfigurationProperties(prefix = "app.submission.routing")
public class SubmissionRoutingProperties {

    public static final String LOCAL = "local";
    public static final String REMOTE = "remote";

    private String mode = LOCAL;

    private boolean cutoverComplete;

    @org.springframework.beans.factory.annotation.Value("${app.runtime.mode:dev-lite}")
    private String runtimeMode = "dev-lite";

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean isRemote() {
        return REMOTE.equals(mode);
    }

    public boolean isCutoverComplete() {
        return cutoverComplete;
    }

    public void setCutoverComplete(boolean cutoverComplete) {
        this.cutoverComplete = cutoverComplete;
    }

    /** Selects the owner read route; local is available only for explicit rollback. */
    public <T> T selectOwnerRead(
            Supplier<T> localSupplier, Supplier<T> remoteSupplier, String operation) {
        if (!"legacy-rollback".equals(runtimeMode) && (!isRemote() || !cutoverComplete)) {
            throw new IllegalStateException(
                    "Submission owner " + operation
                            + " route requires app.submission.routing.mode=remote and "
                            + "app.submission.routing.cutover-complete=true");
        }
        T selected = "legacy-rollback".equals(runtimeMode)
                ? localSupplier.get()
                : remoteSupplier.get();
        if (selected == null) {
            throw new IllegalStateException(
                    ("legacy-rollback".equals(runtimeMode) ? "Local" : "Remote")
                            + " Submission " + operation + " route is unavailable");
        }
        return selected;
    }

    @PostConstruct
    void validate() {
        if (!LOCAL.equals(mode) && !REMOTE.equals(mode)) {
            throw new IllegalStateException(
                    "Invalid app.submission.routing.mode='" + mode
                            + "'; expected 'local' or 'remote'.");
        }
        if (REMOTE.equals(mode) && !cutoverComplete) {
            throw new IllegalStateException(
                    "Remote Submission routing requires app.submission.routing.cutover-complete=true "
                            + "after the schema cutover and grant observation.");
        }
    }
}
