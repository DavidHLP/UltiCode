package com.ulticode.submission.dubbo.provider;

import com.ulticode.modules.submission.port.DefaultSubmissionFencePort;
import com.ulticode.submission.api.service.SubmissionFencePort;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.context.annotation.Profile;

/**
 * Direct Submission-owner provider for the generation and lease fence.
 *
 * <p>Version {@code 1.1.0} gates the wire-incompatible fence change
 * ({@code currentGeneration} returns a nullable {@code Long} instead of
 * {@code Optional<Long>}, which Dubbo cannot serialize reliably): 1.0.0
 * consumers only ever route to 1.0.0 providers, so a mixed rollout fails
 * fast at discovery instead of corrupting return deserialization. Deploy
 * the submission service together with its consumers (app-web, judge).
 */
@DubboService(group = "backend-submission", version = "1.1.0")
@Profile("!test")
@RequiredArgsConstructor
public class SubmissionFenceProvider implements SubmissionFencePort {

    private final DefaultSubmissionFencePort delegate;

    @Override
    public Long currentGeneration(String submissionId) {
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
