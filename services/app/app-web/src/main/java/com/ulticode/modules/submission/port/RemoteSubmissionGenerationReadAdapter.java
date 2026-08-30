package com.ulticode.modules.submission.port;

import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.submission.api.service.SubmissionGenerationReadPort;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Contest adapter for the Submission-owner generation read seam. */
@Component
@Primary
@ConditionalOnExpression("'${app.runtime.mode:dev-lite}' != 'legacy-rollback'")
public class RemoteSubmissionGenerationReadAdapter implements SubmissionGenerationReadPort {

    @DubboReference(group = "backend-submission", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private SubmissionGenerationReadPort submissionGenerationReadPort;

    @Override
    public Long findGenerationForUpdate(String submissionId) {
        return submissionGenerationReadPort.findGenerationForUpdate(submissionId);
    }
}
