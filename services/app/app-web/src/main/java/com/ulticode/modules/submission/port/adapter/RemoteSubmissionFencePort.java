package com.ulticode.modules.submission.port.adapter;

import com.ulticode.app.api.service.SubmissionFencePort;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Remote App route for the Submission owner's generation/lease fence. */
@Component
@ConditionalOnProperty(prefix = "app.submission.routing", name = "mode", havingValue = "remote")
public class RemoteSubmissionFencePort implements SubmissionFencePort {

    @DubboReference(group = "backend-submission", version = "1.0.0",
            timeout = 5000, retries = 0, check = false)
    private SubmissionFencePort submissionOwner;

    @Override
    public Optional<Long> currentGeneration(String submissionId) {
        return submissionOwner.currentGeneration(submissionId);
    }

    @Override
    public boolean acquireLease(String submissionId, String attemptId,
                                long generation, long ttlSeconds) {
        return submissionOwner.acquireLease(submissionId, attemptId, generation, ttlSeconds);
    }

    @Override
    public boolean renewLease(String submissionId, String attemptId, long ttlSeconds) {
        return submissionOwner.renewLease(submissionId, attemptId, ttlSeconds);
    }
}
