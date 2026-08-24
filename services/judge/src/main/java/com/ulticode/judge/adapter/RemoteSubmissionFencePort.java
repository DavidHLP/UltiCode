package com.ulticode.judge.adapter;

import com.ulticode.submission.api.service.SubmissionFencePort;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Judge-side RPC adapter for App-owned lease and generation CAS operations. */
@Component
@Primary
public class RemoteSubmissionFencePort implements SubmissionFencePort {

    @DubboReference(group = "backend-submission", version = "1.1.0",
            timeout = 5000, retries = 0, check = false)
    private SubmissionFencePort submissionFencePort;

    @Override
    public Long currentGeneration(String submissionId) {
        return submissionFencePort.currentGeneration(submissionId);
    }

    @Override
    public boolean acquireLease(String submissionId, String attemptId,
                                long generation, long ttlSeconds) {
        return submissionFencePort.acquireLease(submissionId, attemptId, generation, ttlSeconds);
    }

    @Override
    public boolean renewLease(String submissionId, String attemptId, long ttlSeconds) {
        return submissionFencePort.renewLease(submissionId, attemptId, ttlSeconds);
    }
}
