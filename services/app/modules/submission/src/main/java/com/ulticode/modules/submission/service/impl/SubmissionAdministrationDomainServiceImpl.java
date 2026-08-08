package com.ulticode.modules.submission.service.impl;

import com.ulticode.modules.submission.dto.BatchRejudgeResponse;
import com.ulticode.modules.submission.dto.RejudgeResult;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.port.SubmissionAdministrationWritePort;
import com.ulticode.modules.submission.service.SubmissionAdministrationDomainService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

@Slf4j
public class SubmissionAdministrationDomainServiceImpl implements SubmissionAdministrationDomainService {

    private final SubmissionAdministrationWritePort writePort;

    public SubmissionAdministrationDomainServiceImpl(SubmissionAdministrationWritePort writePort) {
        this.writePort = writePort;
    }

    @Override
    public Optional<Submission> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(writePort.selectById(id));
    }

    @Override
    public RejudgeResult rejudge(String submissionId, boolean notifyUser) {
        log.info("SubmissionAdministrationDomainServiceImpl.rejudge submissionId={} notifyUser={}", submissionId, notifyUser);
        return writePort.rejudgeSubmission(submissionId, notifyUser);
    }

    @Override
    public BatchRejudgeResponse batchRejudge(List<String> submissionIds, boolean notifyUsers) {
        log.info("SubmissionAdministrationDomainServiceImpl.batchRejudge count={} notifyUsers={}",
                submissionIds != null ? submissionIds.size() : 0, notifyUsers);
        return writePort.batchRejudgeSubmissions(submissionIds, notifyUsers);
    }
}
