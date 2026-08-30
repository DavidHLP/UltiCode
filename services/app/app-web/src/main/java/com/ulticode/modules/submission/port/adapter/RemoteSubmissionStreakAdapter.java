package com.ulticode.modules.submission.port.adapter;

import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.submission.api.service.SubmissionStreakPort;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** App adapter for Submission-owner streak reads. */
@Component
@Primary
@ConditionalOnExpression("'${app.runtime.mode:dev-lite}' != 'legacy-rollback'")
public class RemoteSubmissionStreakAdapter implements SubmissionStreakPort {

    @DubboReference(group = "backend-submission", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private SubmissionStreakPort submissionStreakPort;

    @Override
    public int computeStreak(String userId) {
        return submissionStreakPort.computeStreak(userId);
    }
}
