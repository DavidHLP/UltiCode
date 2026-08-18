package com.ulticode.modules.submission.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Selects the single active App-to-Submission writer route during cutover. */
@Configuration
@ConfigurationProperties(prefix = "app.submission.routing")
public class SubmissionRoutingProperties {

    public static final String LOCAL = "local";
    public static final String REMOTE = "remote";

    private String mode = LOCAL;

    private boolean cutoverComplete;

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
