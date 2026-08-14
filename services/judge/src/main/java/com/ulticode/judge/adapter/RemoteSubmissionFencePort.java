package com.ulticode.judge.adapter;

import com.ulticode.app.api.service.SubmissionFencePort;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Judge-side RPC adapter for App-owned lease and generation CAS operations. */
@Component
public class RemoteSubmissionFencePort implements SubmissionFencePort {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = 5000, retries = 0, check = false)
    private SubmissionFencePort delegate;

    @Override
    public Optional<Long> currentGeneration(String submissionId) {
        return delegate.currentGeneration(submissionId);
    }

    @Override
    public boolean acquireLease(String submissionId, String attemptId,
                                long generation, long ttlSeconds) {
        return delegate.acquireLease(submissionId, attemptId, generation, ttlSeconds);
    }

    @Override
    public boolean renewLease(String submissionId, String attemptId, long ttlSeconds) {
        return delegate.renewLease(submissionId, attemptId, ttlSeconds);
    }
}
