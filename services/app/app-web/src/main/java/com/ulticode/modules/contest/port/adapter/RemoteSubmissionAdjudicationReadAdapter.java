package com.ulticode.modules.contest.port.adapter;

import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.submission.api.dto.SubmissionAdjudicationFact;
import com.ulticode.submission.api.service.SubmissionAdjudicationReadPort;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/** App adapter for Submission-owner adjudication drain facts. */
@Component
public class RemoteSubmissionAdjudicationReadAdapter implements SubmissionAdjudicationReadPort {

    @DubboReference(group = "backend-submission", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private SubmissionAdjudicationReadPort submissionAdjudicationReadPort;

    @Override
    public List<SubmissionAdjudicationFact> findByIds(Collection<String> submissionIds) {
        return submissionAdjudicationReadPort.findByIds(submissionIds);
    }
}
