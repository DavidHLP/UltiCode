package com.ulticode.modules.submission.service.impl;

import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.port.SubmissionWritePort;
import com.ulticode.modules.submission.service.SubmissionAdministrationDomainService;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class SubmissionAdministrationDomainServiceImpl implements SubmissionAdministrationDomainService {

    private final SubmissionWritePort writePort;
    private final Clock clock;

    public SubmissionAdministrationDomainServiceImpl(SubmissionWritePort writePort, Clock clock) {
        this.writePort = writePort;
        this.clock = clock;
    }

    @Override
    public Optional<Submission> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(writePort.selectById(id));
    }

    @Override
    public Submission rejudge(String submissionId, boolean notifyUser, String actorId) {
        Submission submission = findById(submissionId)
                .orElseThrow(() -> new BusinessException(BaseErrorCode.NOT_FOUND, "Submission not found"));

        submission.setStatus("PENDING");
        writePort.updateById(submission);
        log.info("Submission rejudged: {} notifyUser={} by actor {}", submissionId, notifyUser, actorId);
        return submission;
    }

    @Override
    public List<Submission> batchRejudge(List<String> submissionIds, boolean notifyUsers, String actorId) {
        if (submissionIds == null || submissionIds.isEmpty()) {
            return List.of();
        }
        List<Submission> rejudgedList = new ArrayList<>();
        for (String id : submissionIds) {
            Optional<Submission> opt = findById(id);
            if (opt.isPresent()) {
                Submission sub = opt.get();
                sub.setStatus("PENDING");
                writePort.updateById(sub);
                rejudgedList.add(sub);
            }
        }
        log.info("Batch rejudge completed count={} notifyUsers={} by actor {}", rejudgedList.size(), notifyUsers, actorId);
        return rejudgedList;
    }
}
