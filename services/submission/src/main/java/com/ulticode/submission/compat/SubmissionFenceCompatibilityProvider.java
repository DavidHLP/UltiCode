package com.ulticode.submission.compat;

import com.ulticode.app.api.service.SubmissionFencePort;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.context.annotation.Profile;

import java.util.Optional;

/** Transitional network seam for the App-owned generation/lease fence. */
@DubboService(group = "backend-submission", version = "1.0.0")
@Profile("!test")
public class SubmissionFenceCompatibilityProvider implements SubmissionFencePort {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = 5000, retries = 0, check = false)
    private SubmissionFencePort appFence;

    @Override
    public Optional<Long> currentGeneration(String submissionId) {
        return appFence.currentGeneration(submissionId);
    }

    @Override
    public boolean acquireLease(String submissionId, String attemptId,
                                long generation, long ttlSeconds) {
        return appFence.acquireLease(submissionId, attemptId, generation, ttlSeconds);
    }

    @Override
    public boolean renewLease(String submissionId, String attemptId, long ttlSeconds) {
        return appFence.renewLease(submissionId, attemptId, ttlSeconds);
    }
}
