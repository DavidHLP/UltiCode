package com.ulticode.modules.submission.port.adapter;

import com.ulticode.app.api.dto.CreateSubmissionDTO;
import com.ulticode.app.api.dto.SubmissionVO;
import com.ulticode.app.api.service.SubmissionWritePort;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Remote App route used only when Submission owner cutover is enabled. */
@Component
@ConditionalOnProperty(prefix = "app.submission.routing", name = "mode", havingValue = "remote")
public class RemoteSubmissionWritePort implements SubmissionWritePort {

    @DubboReference(group = "backend-submission", version = "1.0.0",
            timeout = 10000, retries = 0, check = false)
    private SubmissionWritePort submissionOwner;

    @Override
    public SubmissionVO submit(String userId, CreateSubmissionDTO createDTO) {
        return submissionOwner.submit(userId, createDTO);
    }

    @Override
    public SubmissionVO submitContest(String userId, CreateSubmissionDTO createDTO) {
        return submissionOwner.submitContest(userId, createDTO);
    }

    @Override
    public void updateSubmissionResult(String submissionId, SubmissionStatus status,
                                       int runtime, Double memory, String testDetailsJson) {
        submissionOwner.updateSubmissionResult(submissionId, status, runtime, memory, testDetailsJson);
    }

    @Override
    public boolean updateSubmissionResultFenced(String submissionId, SubmissionStatus status,
                                                int runtime, Double memory, String testDetailsJson,
                                                long generation, String attemptId) {
        return submissionOwner.updateSubmissionResultFenced(
                submissionId, status, runtime, memory, testDetailsJson, generation, attemptId);
    }
}
