package com.ulticode.modules.submission.port;

import com.ulticode.app.api.dto.CreateSubmissionDTO;
import com.ulticode.app.api.dto.SubmissionVO;
import com.ulticode.app.api.service.SubmissionWritePort;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.config.SubmissionRoutingProperties;
import com.ulticode.modules.submission.port.adapter.RemoteSubmissionWritePort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Single App writer route; selects local or remote without enabling dual writes. */
@Component
@Primary
@RequiredArgsConstructor
public class SubmissionWriteRoutingPort implements SubmissionWritePort {

    private final DefaultSubmissionWritePort local;
    private final ObjectProvider<RemoteSubmissionWritePort> remote;
    private final SubmissionRoutingProperties routing;

    private SubmissionWritePort delegate() {
        if (!routing.isRemote()) {
            return local;
        }
        RemoteSubmissionWritePort remotePort = remote.getIfAvailable();
        if (remotePort == null) {
            throw new IllegalStateException("Remote Submission route is enabled but unavailable");
        }
        return remotePort;
    }

    @Override
    public SubmissionVO submit(String userId, CreateSubmissionDTO createDTO) {
        return delegate().submit(userId, createDTO);
    }

    @Override
    public SubmissionVO submitContest(String userId, CreateSubmissionDTO createDTO) {
        return delegate().submitContest(userId, createDTO);
    }

    @Override
    public void updateSubmissionResult(String submissionId, SubmissionStatus status,
                                       int runtime, Double memory, String testDetailsJson) {
        delegate().updateSubmissionResult(submissionId, status, runtime, memory, testDetailsJson);
    }

    @Override
    public boolean updateSubmissionResultFenced(String submissionId, SubmissionStatus status,
                                                int runtime, Double memory, String testDetailsJson,
                                                long generation, String attemptId) {
        return delegate().updateSubmissionResultFenced(
                submissionId, status, runtime, memory, testDetailsJson, generation, attemptId);
    }
}
