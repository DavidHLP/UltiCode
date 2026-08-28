package com.ulticode.modules.submission.port;

import com.ulticode.submission.api.dto.CreateSubmissionDTO;
import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.submission.api.dto.SubmissionFactsSnapshot;
import com.ulticode.submission.api.service.SubmissionIntakePort;
import com.ulticode.submission.api.service.SubmissionVerdictWritePort;
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
public class SubmissionWriteRoutingPort implements SubmissionIntakePort, SubmissionVerdictWritePort {

    private final DefaultSubmissionWritePort local;
    private final ObjectProvider<RemoteSubmissionWritePort> remote;
    private final SubmissionRoutingProperties routing;

    private SubmissionIntakePort intakeDelegate() {
        return routing.select(local, remote::getIfAvailable, "intake");
    }

    private SubmissionVerdictWritePort verdictDelegate() {
        return routing.select(local, remote::getIfAvailable, "verdict");
    }

    @Override
    public SubmissionVO submit(String userId, CreateSubmissionDTO createDTO) {
        return intakeDelegate().submit(userId, createDTO);
    }

    @Override
    public SubmissionVO submitContest(String userId, CreateSubmissionDTO createDTO) {
        return intakeDelegate().submitContest(userId, createDTO);
    }

    @Override
    public SubmissionVO submit(String userId, CreateSubmissionDTO createDTO,
                               SubmissionFactsSnapshot facts) {
        return intakeDelegate().submit(userId, createDTO, facts);
    }

    @Override
    public SubmissionVO submitContest(String userId, CreateSubmissionDTO createDTO,
                                      SubmissionFactsSnapshot facts) {
        return intakeDelegate().submitContest(userId, createDTO, facts);
    }

    @Override
    public void updateSubmissionResult(String submissionId, SubmissionStatus status,
                                       int runtime, Double memory, String testDetailsJson) {
        verdictDelegate().updateSubmissionResult(submissionId, status, runtime, memory, testDetailsJson);
    }

    @Override
    public boolean updateSubmissionResultFenced(String submissionId, SubmissionStatus status,
                                                int runtime, Double memory, String testDetailsJson,
                                                long generation, String attemptId) {
        return verdictDelegate().updateSubmissionResultFenced(
                submissionId, status, runtime, memory, testDetailsJson, generation, attemptId);
    }
}
