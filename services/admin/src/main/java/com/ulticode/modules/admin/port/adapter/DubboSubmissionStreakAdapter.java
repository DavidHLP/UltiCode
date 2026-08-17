package com.ulticode.modules.admin.port.adapter;

import com.ulticode.submission.api.service.SubmissionStreakPort;
import com.ulticode.common.rpc.RpcPolicy;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Admin consumer adapter for App-owned submission streak reads.
 */
@Primary
@Component
public class DubboSubmissionStreakAdapter implements SubmissionStreakPort {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private SubmissionStreakPort submissionStreakPort;

    @Override
    public int computeStreak(String userId) {
        return submissionStreakPort.computeStreak(userId);
    }
}
