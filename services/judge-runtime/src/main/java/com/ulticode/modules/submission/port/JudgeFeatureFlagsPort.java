package com.ulticode.modules.submission.port;

/**
 * Port through which the queue module reads judge feature flags
 * without importing the submission module's config classes.
 *
 * <p>P7-RELOCATE-SUBMISSION-001: extracted when FeatureFlagsProperties
 * relocated to backend-app.
 */
public interface JudgeFeatureFlagsPort {

    /**
     * @return true if the generation fence is enabled for concurrent judge attempts
     */
    boolean isUseGenerationFence();

    /**
     * @return true if the port-based judge queue routing is enabled
     */
    boolean isJudgeQueueUsePort();
}
