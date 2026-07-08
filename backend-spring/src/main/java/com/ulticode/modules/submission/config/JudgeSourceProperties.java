package com.ulticode.modules.submission.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for judge source.
 */
@Configuration
@ConfigurationProperties(prefix = "app.features.judge-source")
public class JudgeSourceProperties {

    private boolean useTestCases = true;

    public boolean isUseTestCases() {
        return useTestCases;
    }

    public void setUseTestCases(boolean useTestCases) {
        this.useTestCases = useTestCases;
    }
}
