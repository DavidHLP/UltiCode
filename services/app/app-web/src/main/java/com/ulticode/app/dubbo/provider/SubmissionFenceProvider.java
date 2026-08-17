package com.ulticode.app.dubbo.provider;

import com.ulticode.submission.api.service.SubmissionFencePort;
import com.ulticode.modules.submission.port.DefaultSubmissionFencePort;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.context.annotation.Profile;

import java.util.Optional;

/** Exposes App-owned generation/lease CAS operations to backend-judge. */
@DubboService(group = "backend-app", version = "1.0.0")
@Profile("!test")
@RequiredArgsConstructor
public class SubmissionFenceProvider implements SubmissionFencePort {

    private final DefaultSubmissionFencePort delegate;

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
