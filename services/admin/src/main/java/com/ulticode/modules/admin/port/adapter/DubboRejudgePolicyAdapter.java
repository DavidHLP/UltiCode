package com.ulticode.modules.admin.port.adapter;

import com.ulticode.submission.api.dto.RejudgeResult;
import com.ulticode.submission.api.service.RejudgePolicy;
import com.ulticode.common.rpc.RpcPolicy;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Admin consumer adapter for the App-owned rejudge policy.
 */
@Primary
@Component
public class DubboRejudgePolicyAdapter implements RejudgePolicy {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private RejudgePolicy rejudgePolicy;

    @Override
    public RejudgeResult rejudge(String submissionId, RejudgeResult rejudgeResult) {
        return rejudgePolicy.rejudge(submissionId, rejudgeResult);
    }
}
