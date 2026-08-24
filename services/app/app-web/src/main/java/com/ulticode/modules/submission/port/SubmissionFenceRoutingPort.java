package com.ulticode.modules.submission.port;

import com.ulticode.submission.api.service.SubmissionFencePort;
import com.ulticode.modules.submission.config.SubmissionRoutingProperties;
import com.ulticode.modules.submission.port.adapter.RemoteSubmissionFencePort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Single App generation/lease route; local and remote paths never dual-write. */
@Component
@Primary
@RequiredArgsConstructor
public class SubmissionFenceRoutingPort implements SubmissionFencePort {

    private final DefaultSubmissionFencePort local;
    private final ObjectProvider<RemoteSubmissionFencePort> remote;
    private final SubmissionRoutingProperties routing;

    private SubmissionFencePort delegate() {
        return routing.select(local, remote::getIfAvailable, "fence");
    }

    @Override
    public Long currentGeneration(String submissionId) {
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
