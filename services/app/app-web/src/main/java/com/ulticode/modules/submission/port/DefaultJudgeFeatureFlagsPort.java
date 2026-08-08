package com.ulticode.modules.submission.port;

import com.ulticode.app.api.service.JudgeFeatureFlagsPort;
import com.ulticode.modules.submission.config.FeatureFlagsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * App-owned adapter exposing judge feature flags to the queue consumer.
 *
 * <p>The queue module depends only on the app-api port and must not import
 * {@link FeatureFlagsProperties} directly.
 */
@Service
@RequiredArgsConstructor
public class DefaultJudgeFeatureFlagsPort implements JudgeFeatureFlagsPort {

    private final FeatureFlagsProperties featureFlags;

    @Override
    public boolean isUseGenerationFence() {
        return featureFlags.isUseGenerationFence();
    }

    @Override
    public boolean isJudgeQueueUsePort() {
        return featureFlags.getJudgeQueue().isUsePort();
    }
}
