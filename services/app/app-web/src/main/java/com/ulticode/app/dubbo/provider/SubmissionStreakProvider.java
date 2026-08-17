package com.ulticode.app.dubbo.provider;

import com.ulticode.submission.api.service.SubmissionStreakPort;
import com.ulticode.modules.submission.port.adapter.SubmissionStreakAdapter;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * Dubbo provider for submission streak reads owned by App.
 */
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class SubmissionStreakProvider implements SubmissionStreakPort {

    private final SubmissionStreakAdapter delegate;

    @Override
    public int computeStreak(String userId) {
        return delegate.computeStreak(userId);
    }
}
