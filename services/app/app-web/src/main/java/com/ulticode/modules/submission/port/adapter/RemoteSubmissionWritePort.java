package com.ulticode.modules.submission.port.adapter;

import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.submission.api.dto.CreateSubmissionDTO;
import com.ulticode.submission.api.dto.SubmissionFactsSnapshot;
import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.submission.api.service.SubmissionIntakePort;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** App intake adapter; every mutation is executed by backend-submission. */
@Component
@Primary
public class RemoteSubmissionWritePort implements SubmissionIntakePort {

    @DubboReference(group = "backend-submission", version = "1.0.0", timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private SubmissionIntakePort submissionIntake;

    @Override
    public SubmissionVO submit(String userId, CreateSubmissionDTO createDTO,
                               SubmissionFactsSnapshot facts) {
        return submissionIntake.submit(userId, createDTO, facts);
    }

    @Override
    public SubmissionVO submitContest(String userId, CreateSubmissionDTO createDTO,
                                      SubmissionFactsSnapshot facts) {
        return submissionIntake.submitContest(userId, createDTO, facts);
    }
}
