package com.ulticode.modules.submission.port.adapter;

import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.submission.api.service.SubmissionReadPort;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/** App adapter for Submission-owner contest submission projections. */
@Component
@Primary
public class RemoteSubmissionReadAdapter implements SubmissionReadPort {

    @DubboReference(group = "backend-submission", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private SubmissionReadPort submissionReadPort;

    @Override
    public SubmissionVO toVO(String submissionId) {
        return submissionReadPort.toVO(submissionId);
    }

    @Override
    public List<SubmissionVO> toVOs(Collection<String> submissionIds) {
        return submissionReadPort.toVOs(submissionIds);
    }
}
