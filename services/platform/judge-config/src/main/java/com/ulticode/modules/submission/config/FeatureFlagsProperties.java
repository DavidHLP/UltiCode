package com.ulticode.modules.submission.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/** Single source of truth for the live Submission/Judge runtime flags. */
@Configuration
@ConfigurationProperties(prefix = "app.features")
public class FeatureFlagsProperties {

    private boolean firstSolveNotificationsEnabled = true;
    private boolean contestAnalyticsEnabled = true;
    private boolean useJudgeOutbox;
    private boolean useGenerationFence;

    @NestedConfigurationProperty
    private JudgeQueue judgeQueue = new JudgeQueue();

    public boolean isFirstSolveNotificationsEnabled() {
        return firstSolveNotificationsEnabled;
    }

    public void setFirstSolveNotificationsEnabled(boolean enabled) {
        firstSolveNotificationsEnabled = enabled;
    }

    public boolean isContestAnalyticsEnabled() {
        return contestAnalyticsEnabled;
    }

    public void setContestAnalyticsEnabled(boolean enabled) {
        contestAnalyticsEnabled = enabled;
    }

    public boolean isUseJudgeOutbox() {
        return useJudgeOutbox;
    }

    public void setUseJudgeOutbox(boolean enabled) {
        useJudgeOutbox = enabled;
    }

    public boolean isUseGenerationFence() {
        return useGenerationFence;
    }

    public void setUseGenerationFence(boolean enabled) {
        useGenerationFence = enabled;
    }

    public JudgeQueue getJudgeQueue() {
        return judgeQueue;
    }

    public void setJudgeQueue(JudgeQueue judgeQueue) {
        this.judgeQueue = judgeQueue;
    }

    public static class JudgeQueue {
        private boolean usePort;

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private LocalDateTime cutoverAt;

        public boolean isUsePort() {
            return usePort;
        }

        public void setUsePort(boolean usePort) {
            this.usePort = usePort;
        }

        public LocalDateTime getCutoverAt() {
            return cutoverAt;
        }

        public void setCutoverAt(LocalDateTime cutoverAt) {
            this.cutoverAt = cutoverAt;
        }
    }
}
