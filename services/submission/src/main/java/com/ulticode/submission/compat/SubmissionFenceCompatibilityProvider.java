package com.ulticode.submission.compat;

import com.ulticode.modules.submission.port.DefaultSubmissionFencePort;
import com.ulticode.submission.api.service.SubmissionFencePort;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;

import java.util.Optional;

/**
 * Transitional network seam for the Submission-owned generation/lease fence.
 *
 * <p>The default {@code compat} mode forwards to the App-owned fence so there
 * is exactly one generation/lease writer while the App route remains local.
 * {@code local} delegates to the Submission-schema fence after the authorized
 * runtime cutover and grant transition.
 */
@DubboService(group = "backend-submission", version = "1.0.0")
@Profile("!test")
public class SubmissionFenceCompatibilityProvider implements SubmissionFencePort {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = 5000, retries = 0, check = false)
    private SubmissionFencePort appFence;

    private final ObjectProvider<DefaultSubmissionFencePort> localFence;

    @Value("${app.submission.owner.mode:compat}")
    private String ownerMode;

    /** Default constructor keeps the compat-only contract test path working. */
    public SubmissionFenceCompatibilityProvider() {
        this.localFence = null;
    }

    @Autowired
    public SubmissionFenceCompatibilityProvider(
            ObjectProvider<DefaultSubmissionFencePort> localFence) {
        this.localFence = localFence;
    }

    private SubmissionFencePort delegate() {
        if ("local".equals(ownerMode)) {
            if (localFence == null) {
                throw new IllegalStateException(
                        "app.submission.owner.mode=local but no local fence provider wired");
            }
            DefaultSubmissionFencePort local = localFence.getIfAvailable();
            if (local == null) {
                throw new IllegalStateException(
                        "app.submission.owner.mode=local but no local DefaultSubmissionFencePort bean");
            }
            return local;
        }
        return appFence;
    }

    @Override
    public Optional<Long> currentGeneration(String submissionId) {
        return delegate().currentGeneration(submissionId);
    }

    @Override
    public boolean acquireLease(String submissionId, String attemptId,
                                long generation, long ttlSeconds) {
        return delegate().acquireLease(submissionId, attemptId, generation, ttlSeconds);
    }

    @Override
    public boolean renewLease(String submissionId, String attemptId, long ttlSeconds) {
        return delegate().renewLease(submissionId, attemptId, ttlSeconds);
    }
}
