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
        if (createDTO != null && createDTO.getContestId() != null && routing.isRemote()) {
            // CR P1-2: contest submissions must stay in the local App
            // transaction. ContestServiceImpl.submitContestProblem holds the
            // contest row FOR UPDATE across this call; the remote route would
            // re-enter App through the submission service and re-lock the same
            // row in a second transaction, deadlocking until the Dubbo timeout.
            // Contest admission + write stay in one owner transaction until the
            // whole contest command moves behind a single owner RPC.
            return local.submit(userId, createDTO);
        }
        return delegate().submit(userId, createDTO);
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
