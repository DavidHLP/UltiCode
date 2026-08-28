package com.ulticode.judge.adapter;

import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.submission.api.service.SubmissionVerdictWritePort;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Judge adapter exposing only the verdict capability it uses. */
@Component
@Primary
public class RemoteSubmissionVerdictWritePort implements SubmissionVerdictWritePort {

    @DubboReference(group = "backend-submission", version = "1.0.0",
            timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private SubmissionVerdictWritePort submissionVerdict;

    @Override
    public void updateSubmissionResult(String submissionId, SubmissionStatus status,
                                       int runtime, Double memory, String testDetailsJson) {
        submissionVerdict.updateSubmissionResult(
                submissionId, status, runtime, memory, testDetailsJson);
    }

    @Override
    public boolean updateSubmissionResultFenced(String submissionId, SubmissionStatus status,
                                                int runtime, Double memory, String testDetailsJson,
                                                long generation, String attemptId) {
        return submissionVerdict.updateSubmissionResultFenced(
                submissionId, status, runtime, memory, testDetailsJson, generation, attemptId);
    }
}
