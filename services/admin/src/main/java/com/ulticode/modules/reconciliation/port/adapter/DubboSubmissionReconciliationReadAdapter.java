package com.ulticode.modules.reconciliation.port.adapter;

import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.submission.api.dto.SubmissionUserReferenceCountDTO;
import com.ulticode.submission.api.service.SubmissionReconciliationReadPort;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** Admin adapter for the Submission owner's bounded reconciliation facts. */
@Component
public class DubboSubmissionReconciliationReadAdapter implements SubmissionReconciliationReadPort {

    @DubboReference(group = "backend-submission", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private SubmissionReconciliationReadPort submissionReconciliationReadPort;

    @Override
    public List<SubmissionUserReferenceCountDTO> findUserReferenceCounts(
            String afterAccountId,
            LocalDateTime createdSince,
            int limit) {
        return submissionReconciliationReadPort.findUserReferenceCounts(
                afterAccountId, createdSince, limit);
    }
}
