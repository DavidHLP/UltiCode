package com.ulticode.judge.adapter;

import com.ulticode.submission.api.dto.CreateSubmissionDTO;
import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.submission.api.dto.SubmissionFactsSnapshot;
import com.ulticode.submission.api.service.SubmissionWritePort;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Judge-side RPC adapter for App-owned verdict writes. */
@Component
@Primary
public class RemoteSubmissionWritePort implements SubmissionWritePort {

    @DubboReference(group = "backend-submission", version = "1.0.0",
            timeout = 10000, retries = 0, check = false)
    private SubmissionWritePort submissionWritePort;

    @Override
    public SubmissionVO submit(String userId, CreateSubmissionDTO createDTO) {
        throw new UnsupportedOperationException("backend-judge does not accept submissions");
    }

    @Override
    public SubmissionVO submitContest(String userId, CreateSubmissionDTO createDTO) {
        throw new UnsupportedOperationException("backend-judge does not accept submissions");
    }

    @Override
    public SubmissionVO submit(String userId, CreateSubmissionDTO createDTO,
                               SubmissionFactsSnapshot facts) {
        throw new UnsupportedOperationException("backend-judge does not accept submissions");
    }

    @Override
    public SubmissionVO submitContest(String userId, CreateSubmissionDTO createDTO,
                                      SubmissionFactsSnapshot facts) {
        throw new UnsupportedOperationException("backend-judge does not accept submissions");
    }

    @Override
    public void updateSubmissionResult(String submissionId, SubmissionStatus status,
                                       int runtime, Double memory, String testDetailsJson) {
        submissionWritePort.updateSubmissionResult(submissionId, status, runtime, memory, testDetailsJson);
    }

    @Override
    public boolean updateSubmissionResultFenced(String submissionId, SubmissionStatus status,
                                                int runtime, Double memory, String testDetailsJson,
                                                long generation, String attemptId) {
        return submissionWritePort.updateSubmissionResultFenced(
                submissionId, status, runtime, memory, testDetailsJson, generation, attemptId);
    }
}
