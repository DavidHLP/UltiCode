package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.dto.RejudgeResult;
import com.ulticode.app.api.service.RejudgePolicy;
import com.ulticode.modules.submission.port.impl.DefaultRejudgePolicy;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * Dubbo provider for the App-owned rejudge state machine.
 */
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class RejudgePolicyProvider implements RejudgePolicy {

    private final DefaultRejudgePolicy delegate;

    @Override
    public RejudgeResult rejudge(String submissionId, RejudgeResult rejudgeResult) {
        return delegate.rejudge(submissionId, rejudgeResult);
    }
}
